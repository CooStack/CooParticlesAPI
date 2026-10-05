package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.EventsInitializationEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientStoppingEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientResourceReloadEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientFrameStartEvent
import cn.coostack.cooparticlesapi.event.events.entity.client.ClientEntityUnloadEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.client.Minecraft
import net.minecraft.client.GraphicsStatus
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.PackType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ChunkPos
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.IdentityHashMap

/**
 * 管理结构实体的 GPU 网格、光照失效和退出清理；由 [init] 注册客户端生命周期。
 * 渲染线程独占资源，光照回调只投递失效通知，不跨线程操作 GPU。
 */
@EventListener(dist = DistType.CLIENT)
internal object StructureModelMeshes {
    /** 有界、按实体身份隔离的资源缓存，避免同 ID、跨世界或弱引用回收时泄漏显存。 */
    private val meshes = ModelMeshCache<StructureModelEntity, ModelGpuMesh>(67108864, 256)
    /** 从光照回调投递的世界区段，不直接触及渲染线程资源。 */
    private val changedSections = ConcurrentLinkedQueue<Long>()
    /** 区块内容及生物群系更新，按水平列定位受影响的缓存。 */
    private val changedChunks = ConcurrentLinkedQueue<Long>()
    /** 超出单份预算的快照不反复尝试上传，资源或快照变化后才重新尝试。 */
    private val oversized = IdentityHashMap<StructureModelEntity, StructureSnapshot>()
    /** 客户端 tick 序号，不受世界时间命令影响。 */
    private var ticks = 0L
    /** 每个世界帧允许的新建数量，分散首次看到大量结构时的上传开销。 */
    private var buildsLeft = 0
    /** 当前帧是否允许持久网格；光影开启或检测失败时走原路径。 */
    private var supported = false
    /** 可选 Iris 公共 API 查询，不依赖 Iris 私有渲染实现。 */
    private var shadersEnabled: () -> Boolean = { false }
    /** Sodium 公共纹理 API；未安装 Sodium 时无需通知动画可见性。 */
    private var activateSprite: (TextureAtlasSprite) -> Unit = {}
    /** Sodium 可见纹理接口不可用时禁止持久缓存，避免动画冻结。 */
    private var textureTrackingSupported = true
    /** 最近采用的画质设置，用于及时丢弃旧 AO、树叶层及混色缓存。 */
    private var appearance: Triple<GraphicsStatus, Boolean, Int>? = null
    /** 画质变更或资源重载后递增，通知渲染器重新分组材质层。 */
    var generation = 0L
        private set

    /** 注册资源重载、世界帧、实体卸载和退出清理；仅在客户端初始化时调用一次。 */
    @EventHandler
    fun init(event: EventsInitializationEvent) {
        if (CooParticlesServices.PLATFORM.isModLoaded("sodium")) {
            try {
                val api = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil")
                val instance = api.getField("INSTANCE").get(null)
                val mark = api.getMethod("markSpriteActive", TextureAtlasSprite::class.java)
                activateSprite = { sprite -> mark.invoke(instance, sprite) }
            } catch (failure: ReflectiveOperationException) {
                textureTrackingSupported = false
                CooParticlesConstants.logger.warn("Sodium 纹理 API 不可用，结构网格缓存将回退到即时渲染", failure)
            }
        }
        if (CooParticlesServices.PLATFORM.isModLoaded("iris")) {
            shadersEnabled = try {
                val api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
                val instance = api.getMethod("getInstance").invoke(null)
                val query = api.getMethod("isShaderPackInUse")
                val check: () -> Boolean = { query.invoke(instance) as Boolean }
                check
            } catch (failure: ReflectiveOperationException) {
                CooParticlesConstants.logger.warn("无法查询 Iris 公共 API，结构网格缓存将回退到即时渲染", failure)
                val disabled: () -> Boolean = { true }
                disabled
            }
        }
    }

    /** 主渲染帧重置构建预算并处理光照失效。 */
    @EventHandler
    fun frame(event: ClientFrameStartEvent) {
            buildsLeft = 2
            val options = Minecraft.getInstance().options
            val current = Triple(options.graphicsMode().get(), options.ambientOcclusion().get(), options.biomeBlendRadius().get())
            if (appearance != current) {
                clear()
                appearance = current
            }
            supported = try {
                textureTrackingSupported && !shadersEnabled() && options.graphicsMode().get() != GraphicsStatus.FABULOUS
            } catch (failure: ReflectiveOperationException) {
                CooParticlesConstants.logger.warn("Iris 状态查询失败，结构网格缓存已停用", failure)
                shadersEnabled = { true }
                false
            }
            if (!supported) meshes.clear()
            applyInvalidations()
    }

    /** 定期清理死亡、切换世界和超时的网格。 */
    @EventHandler
    fun tick(event: ClientPostTickEvent) {
            val client = Minecraft.getInstance()
            ticks++
            meshes.removeIf { entity, mesh ->
                entity.level() !== client.level || entity.isRemoved || !entity.isAlive || ticks - mesh.lastUsed > 200
            }
            oversized.keys.removeIf { it.level() !== client.level || it.isRemoved || !it.isAlive }
    }

    /** 卸载实体时立即释放，不等待弱引用回收。 */
    @EventHandler
    fun unload(event: ClientEntityUnloadEvent) {
            val entity = event.entity
            if (entity is StructureModelEntity) {
                meshes.remove(entity)
                oversized.remove(entity)
            }
    }

    /** 资源应用阶段释放所有旧材质网格。 */
    @EventHandler
    fun reload(event: ClientResourceReloadEvent) { clear() }

    /** 世界更换时清空旧世界的光照通知。 */
    @EventHandler
    fun change(event: ClientWorldChangeEvent) { clear() }

    /** 图形上下文销毁前关闭缓冲区。 */
    @EventHandler
    fun stop(event: ClientStoppingEvent) { clear() }

    /** 仅缓存已核对顶点布局与状态的原版非透明层；未知层继续使用原调用方。 */
    fun supportsLayer(layer: RenderType): Boolean =
        layer === RenderType.solid() || layer === RenderType.cutout() || layer === RenderType.cutoutMipped()

    /**
     * 命中时直接绘制，否则在帧预算内构建；返回假时调用方必须绘制原路径。
     * 示例：`draw(entity, origin, consumers) { mesh -> mesh.buildLayer(layer, emit) }`。
     * @param entity 已加入客户端世界的结构实体
     * @param origin 当前插值结构原点，与光照采样一致
     * @param consumers 原始顶点提供者，非主世界提供者禁止绕过其语义
     * @param build 只在未命中时填充网格，异常时新资源会关闭
     * @return 是否已经绘制缓存负责的非透明层
     */
    fun draw(entity: StructureModelEntity, origin: Vec3, consumers: MultiBufferSource,
             build: (ModelGpuMesh) -> Unit): Boolean {
        val client = Minecraft.getInstance()
        if (!supported || client.shouldEntityAppearGlowing(entity) ||
            consumers !== client.renderBuffers().bufferSource()) return false
        applyInvalidations()
        val snapshot = entity.snapshot ?: return false
        if (oversized[entity] === snapshot) return false
        var mesh = meshes[entity]
        if (mesh != null && !mesh.matches(snapshot, entity.settings, origin)) {
            meshes.remove(entity)
            mesh = null
        }
        if (mesh == null) {
            if (buildsLeft <= 0) return false
            buildsLeft--
            val created = ModelGpuMesh(snapshot, entity.settings, origin)
            try {
                build(created)
            } catch (failure: Throwable) {
                created.close()
                throw failure
            }
            if (!meshes.put(entity, created, created.bytes)) {
                oversized[entity] = snapshot
                return false
            }
            mesh = created
        }
        mesh.lastUsed = ticks
        try {
            mesh.sprites.forEach(activateSprite)
        } catch (failure: ReflectiveOperationException) {
            textureTrackingSupported = false
            supported = false
            meshes.clear()
            CooParticlesConstants.logger.warn("Sodium 纹理激活失败，结构网格缓存已停用", failure)
            return false
        }
        mesh.draw(client.gameRenderer.mainCamera.position)
        return true
    }

    /**
     * 光照引擎通知某世界区段更新；只入队，可从光照线程调用。
     * @param section 原版 ChunkSectionPos 打包坐标，例如 `sectionPos.asLong()`
     */
    @JvmStatic
    fun invalidateSection(section: Long) {
        changedSections.add(section)
    }

    /**
     * 区块列重新加载、卸载或更新生物群系时投递失效通知。
     * @param x 区块列 X 坐标，不是方块坐标
     * @param z 区块列 Z 坐标，不是方块坐标
     */
    @JvmStatic
    fun invalidateChunk(x: Int, z: Int) {
        changedChunks.add(ChunkPos.asLong(x, z))
    }

    /** 合并本轮通知后标记失效，不在通知线程或不可见时提前重建模型。 */
    private fun applyInvalidations() {
        if (changedSections.isEmpty() && changedChunks.isEmpty()) return
        val sections = LongOpenHashSet()
        val chunks = LongOpenHashSet()
        while (true) {
            val section = changedSections.poll() ?: break
            sections.add(section)
        }
        while (true) {
            val chunk = changedChunks.poll() ?: break
            chunks.add(chunk)
        }
        meshes.forEach { _, mesh ->
            if (mesh.sections.any { sections.contains(it) } || chunks.any {
                    mesh.dependsOnChunk(ChunkPos.getX(it), ChunkPos.getZ(it))
                }) mesh.dirty = true
        }
    }

    /** 清理独占 GPU 资源和待处理通知，只由客户端或资源应用线程调用。 */
    private fun clear() {
        meshes.clear()
        changedSections.clear()
        changedChunks.clear()
        oversized.clear()
        generation++
    }
}
