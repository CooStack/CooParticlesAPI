package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.render.CParticleRenderer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGridManager
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import org.joml.Matrix4f

/**
 * Manager 内部使用的完整批次键。
 *
 * 示例：相同基础纹理配不同蒙版时会生成两个键。
 * 禁止：不要把该内部键暴露为公共 API，旧 [CParticleSystemKey] 需要保持 JVM 兼容。
 *
 * @property name 调用方使用的逻辑系统名
 * @property mode 系统更新模式
 * @property layer 混合与深度状态
 * @property textureBindingKey 基础纹理 binding
 * @property maskTextureBindingKey 可选蒙版纹理 binding
 */
private data class ManagedCParticleSystemKey(
    val name: String,
    val mode: CParticleSystemMode,
    val layer: CParticleRenderLayer,
    val textureBindingKey: CParticleTextureBindingKey,
    val maskTextureBindingKey: CParticleTextureBindingKey?,
)

/**
 * # CParticleSystemManager — GPU 粒子系统客户端总管
 *
 * 生命周期挂载点:
 * - tick: `CooParticlesAPIClient.tickClient` (支持 tickrate 补偿)
 * - render: `ParticleEngine.render` 粒子阶段 (fabric/neoforge mixin)
 * - clear: 断线/换世界 (`clearTransientClientState`)
 * - reload: 资源重载 (`reloadShaderPrograms`)
 *
 * 所有方法都只应在客户端渲染线程调用 (MC 客户端 tick 与渲染同线程).
 */
object CParticleSystemManager {

    /** Emitter 未覆写时，CParticle 方块碰撞相对 system 原点的保证范围。 */
    const val DEFAULT_BLOCK_COLLISION_RANGE = 24

    /** 单个 emitter 可声明的最大 CParticle 方块碰撞范围，防止误配置创建超大稠密网格。 */
    const val MAX_BLOCK_COLLISION_RANGE = 96

    private val systems = LinkedHashMap<ManagedCParticleSystemKey, CParticleSystem>()

    /** 全局开关 */
    @JvmStatic
    var enabled = true

    /**
     * 所有 CParticle system 合计允许的存活粒子数。
     *
     * 示例：客户端启动时用 `APIConfig.cparticleCountLimit` 配置该值。
     * 禁止：不要把单个 system 的槽位容量当成全局上限。
     */
    @get:JvmStatic
    var particleCountLimit = 3_000_000
        private set

    /** 把 emitter 声明限制到碰撞网格支持的安全范围。 */
    internal fun normalizeBlockCollisionRange(range: Int): Int =
        range.coerceIn(0, MAX_BLOCK_COLLISION_RANGE)

    /**
     * 当前所有 CParticle system 中的存活槽位数。
     *
     * 示例：新粒子占用槽位后该值增加，死亡或清空后减少。
     * 禁止：不能从 manager 的 system map 重新求和，否则会漏掉未注册 system。
     */
    private var globalAliveCount = 0

    private var currentTick = 0
    private var fabricParticlePassIndex = 0
    private var renderFrameId = 0L
    private var deferredTerrainForegroundFrame = -1L
    private var deferredTerrainForegroundScenePost = false
    internal val currentRenderFrameId: Long
        get() = renderFrameId

    private val lastNonEmptyTick = HashMap<ManagedCParticleSystemKey, Int>()
    private val autoRelease = HashSet<ManagedCParticleSystemKey>()
    private val terminalAutoRelease = HashSet<ManagedCParticleSystemKey>()

    // ------------------------------------------------------------ 系统管理

    /**
     * 更新所有 GPU 粒子系统共享的存活数量上限。
     *
     * 示例：`configureParticleCountLimit(config.cparticleCountLimit)`。
     * 禁止：小于 `1` 的值不会关闭系统，而是按 `1` 处理。
     *
     * @param limit 新的全局存活粒子数上限
     */
    @JvmStatic
    fun configureParticleCountLimit(limit: Int) {
        particleCountLimit = limit.coerceAtLeast(1)
    }

    /**
     * 检查所有 CParticle system 的当前存活总数是否仍低于全局上限。
     *
     * 示例：新粒子写入槽位前调用本方法。
     * 禁止：该结果只在客户端渲染线程当前调用链内有效，不能跨线程缓存。
     *
     * @return 仍可接收至少一个新 GPU 粒子时返回 `true`
     */
    internal fun hasAvailableParticleCapacity(): Boolean {
        return globalAliveCount < particleCountLimit
    }

    /**
     * 为一个新 GPU 粒子申请全局槽位。
     *
     * 示例：Store 确认本地仍有空槽后调用本方法。
     * 禁止：仅检查 [hasAvailableParticleCapacity] 不能占用额度。
     *
     * @return 申请成功时返回 `true`；达到配置上限时返回 `false`
     */
    internal fun tryAcquireParticleSlot(): Boolean {
        if (!hasAvailableParticleCapacity()) return false
        globalAliveCount++
        return true
    }

    /**
     * 归还已经释放的全局 GPU 粒子槽位。
     *
     * 示例：Store 清空三个存活粒子后调用 `releaseParticleSlots(3)`。
     * 禁止：不能重复归还同一槽位，也不能传入负数。
     *
     * @param count 本次释放的存活槽位数
     */
    internal fun releaseParticleSlots(count: Int) {
        require(count in 0..globalAliveCount) {
            "Cannot release $count CParticle slots while only $globalAliveCount are alive"
        }
        globalAliveCount -= count
    }

    /**
     * 获取或创建一个粒子系统.
     *
     * @param autoReleaseWhenEmpty true 时系统空置 200 tick 后自动销毁
     *   (emitter 绑定的系统用)
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        autoReleaseWhenEmpty: Boolean = false,
    ): CParticleSystem = getOrCreateSystem(
        name,
        capacity,
        layer,
        mode,
        CParticleTextureBindingKey.PARTICLE_ATLAS,
        autoReleaseWhenEmpty,
        null,
    )

    /**
     * 获取或创建一个绑定到指定基础纹理的 system。
     *
     * 示例：end rod 基础纹理可以按方块图集蒙版复用一个 system。
     * 禁止：任一纹理 binding 不同的 system 都不能共享实例槽位。
     *
     * @param name 逻辑系统名
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 基础纹理或图集 binding
     * @param autoReleaseWhenEmpty 空置后是否自动销毁
     * @return 完整 key 对应的系统
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        textureBindingKey: CParticleTextureBindingKey,
        autoReleaseWhenEmpty: Boolean = false,
    ): CParticleSystem = getOrCreateSystem(
        name,
        capacity,
        layer,
        mode,
        textureBindingKey,
        autoReleaseWhenEmpty,
        null,
    )

    /**
     * 获取或创建同时匹配基础纹理和蒙版纹理的 system。
     *
     * 示例：`maskTextureBindingKey = BLOCK_ATLAS` 会与无蒙版批次分开。
     * 禁止：不能省略 [maskTextureBindingKey] 后期待匹配已有蒙版批次。
     *
     * @param name 逻辑系统名
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 基础纹理 binding
     * @param autoReleaseWhenEmpty 空置后是否自动销毁
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 完整批次键对应的系统
     */
    @JvmStatic
    fun getOrCreateSystem(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        textureBindingKey: CParticleTextureBindingKey,
        autoReleaseWhenEmpty: Boolean,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem {
        val key = ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey)
        systems[key]?.let {
            if (autoReleaseWhenEmpty) autoRelease.add(key)
            return it
        }
        val system = CParticleSystem(name, capacity, layer, mode, textureBindingKey, maskTextureBindingKey)
        systems[key] = system
        lastNonEmptyTick[key] = currentTick
        if (autoReleaseWhenEmpty) autoRelease.add(key)
        return system
    }

    @JvmStatic
    fun getSystem(name: String): CParticleSystem? =
        systems.entries.firstOrNull { it.key.name == name }?.value

    /**
     * 按完整系统键查询，不会误取同名的其他 binding 变体。
     *
     * 示例：composition 绑定模板纹理后用此方法检查容量。
     * 禁止：旧的模糊名称查询不适合决定某个精确批次是否存在。
     *
     * @return 完整键匹配的系统，未创建时返回 `null`
     */
    @JvmStatic
    fun getSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem? = getSystem(name, mode, layer, textureBindingKey, null)

    /**
     * 按基础纹理和蒙版纹理的完整批次键查询系统。
     *
     * 示例：`maskTextureBindingKey = null` 只查询无蒙版批次。
     * 禁止：不要用另一张蒙版图集的 binding 查询当前批次。
     *
     * @param name 逻辑系统名
     * @param mode 更新模式
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 完整键匹配的系统，未创建时返回 `null`
     */
    @JvmStatic
    fun getSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem? = systems[
        ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey)
    ]

    /**
     * 共享的默认 SIMULATED 系统 (按渲染层区分) — 散粒子直接往这里生成
     */
    @JvmStatic
    fun defaultSystem(layer: CParticleRenderLayer): CParticleSystem =
        defaultSystem(layer, CParticleTextureBindingKey.PARTICLE_ATLAS)

    /**
     * 返回指定渲染层、基础纹理绑定和可选蒙版绑定的共享 SIMULATED 系统。
     *
     * 示例：所有方块图集散粒子共用一个默认系统。
     * 禁止：不要把独立纹理传入方块图集系统。
     *
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理绑定
     * @return 可直接接收已解析实例的共享系统
     */
    @JvmStatic
    fun defaultSystem(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem = defaultSystem(layer, textureBindingKey, null)

    /**
     * 返回指定基础纹理和蒙版纹理 binding 的共享 SIMULATED 系统。
     *
     * 示例：粒子图集基础纹理和方块图集蒙版共用一个匹配批次。
     * 禁止：不同蒙版 binding 不能返回同一个系统。
     *
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     * @return 可直接接收匹配实例的共享系统
     */
    @JvmStatic
    fun defaultSystem(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleSystem =
        getOrCreateSystem(
            "default/${layer.name.lowercase()}",
            655360,
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
            autoReleaseWhenEmpty = false,
            maskTextureBindingKey = maskTextureBindingKey,
        )
            .also {
                // 默认池承载任意位置的散粒子, 不做整池距离剔除
                it.visibleRange = Double.MAX_VALUE
            }

    /** 快速生成一个散粒子到默认系统 (无力场; 需要力场请自建系统) */
    @JvmStatic
    fun spawn(particle: CParticle, layer: CParticleRenderLayer = CParticleRenderLayer.TRANSLUCENT): Int {
        if (!ready()) return -1
        val resolved = particle.resolveTextures(particle.pos)
        if (!resolved.isValid) return -1
        return defaultSystem(layer, resolved.base.bindingKey, resolved.mask?.bindingKey)
            .spawnResolved(particle, resolved)
    }

    @JvmStatic
    fun removeSystem(name: String) {
        val matchingKeys = systems.keys.filter { it.name == name }
        matchingKeys.forEach(::removeSystem)
    }

    /** 删除一个完整键对应的系统，不影响同名的其他 binding 变体。 */
    @JvmStatic
    fun removeSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ) {
        removeSystem(name, mode, layer, textureBindingKey, null)
    }

    /**
     * 删除一个基础纹理和蒙版纹理都匹配的系统。
     *
     * 示例：删除方块蒙版批次不会影响同名的无蒙版批次。
     * 禁止：不要用模糊名称删除只应移除的单个批次。
     *
     * @param name 逻辑系统名
     * @param mode 更新模式
     * @param layer 混合与深度状态
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     */
    @JvmStatic
    fun removeSystem(
        name: String,
        mode: CParticleSystemMode,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ) {
        removeSystem(ManagedCParticleSystemKey(name, mode, layer, textureBindingKey, maskTextureBindingKey))
    }

    private fun removeSystem(key: ManagedCParticleSystemKey) {
        systems.remove(key)?.release()
        lastNonEmptyTick.remove(key)
        autoRelease.remove(key)
        terminalAutoRelease.remove(key)
    }

    /**
     * 标记名称前缀匹配的自动回收 systems 不再接收新粒子。
     * 已空的 system 当场释放，仍有粒子的 system 在归零后的第一个 tick 释放。
     *
     * @param namePrefix system 名称前缀
     */
    internal fun releaseSystemsWhenEmpty(namePrefix: String) {
        val matchingKeys = systems.keys.filter { key ->
            key in autoRelease && key.name.startsWith(namePrefix)
        }
        for (key in matchingKeys) {
            val system = systems[key] ?: continue
            if (system.store.aliveCount == 0) {
                removeSystem(key)
            } else {
                terminalAutoRelease.add(key)
            }
        }
    }

    /**
     * 更新名称前缀匹配的全部 system 的方块碰撞范围。
     *
     * 示例：emitter 新建 segment 后，同步更新该 emitter 仍存活的旧 segment。
     * 禁止：[namePrefix] 不能为空，避免误改所有 system。
     */
    internal fun updateBlockCollisionRange(namePrefix: String, range: Int) {
        require(namePrefix.isNotEmpty())
        val normalizedRange = normalizeBlockCollisionRange(range)
        for ((key, system) in systems) {
            if (key.name.startsWith(namePrefix)) {
                system.blockCollisionRange = normalizedRange
            }
        }
    }

    /**
     * 判断自动回收 system 当前是否可以释放。
     * terminal system 不再等待空闲阈值，但任何仍有粒子的 system 都不能提前销毁。
     */
    internal fun shouldReleaseAutoSystem(aliveCount: Int, terminal: Boolean, idleTicks: Int): Boolean {
        val autoReleaseIdleTicks = 200
        return aliveCount == 0 && (terminal || idleTicks > autoReleaseIdleTicks)
    }

    /**
     * 返回所有 CParticle system 的总存活粒子数。
     *
     * 示例：调试界面可用 `totalAlive()` 显示当前全局占用。
     * 禁止：不能只把它理解为 manager 内部 map 的存活数。
     *
     * @return 当前占用全局额度的粒子数
     */
    @JvmStatic
    fun totalAlive(): Int = globalAliveCount

    @JvmStatic
    fun systemCount(): Int = systems.size

    private fun ready(): Boolean {
        if (!enabled) return false
        CParticleCapabilities.detect()
        return CParticleCapabilities.instancingSupported
    }

    // ------------------------------------------------------------ 生命周期

    /** 每次 LevelRenderer 帧开始时重置 Fabric Iris MIXED 分流计数。 */
    @JvmStatic
    fun beginRenderFrame() {
        fabricParticlePassIndex = 0
        renderFrameId++
    }

    /** Fabric 的 Iris MIXED 模式会调用两次 ParticleEngine。 */
    @JvmStatic
    fun renderFabricParticlePass(camera: Camera, partial: Float) {
        val pass = if (IrisCompat.usesMixedParticleRendering()) {
            when (fabricParticlePassIndex++) {
                0 -> CParticleRenderPass.OPAQUE
                1 -> CParticleRenderPass.TRANSLUCENT
                else -> CParticleRenderPass.NONE
            }
        } else {
            if (fabricParticlePassIndex++ == 0) CParticleRenderPass.ALL else CParticleRenderPass.NONE
        }
        renderParticlePass(camera, partial, pass)
    }

    /**
     * 在原版粒子阶段绘制当前 pass 覆盖的 GPU 粒子。
     *
     * 示例：Iris MIXED 的第二次回调传入 [CParticleRenderPass.TRANSLUCENT]。
     * 禁止从世界渲染事件重复调用，否则同一帧会绘制两次。
     *
     * @param camera 当前渲染相机
     * @param partial tick 插值
     * @param pass 本次允许绘制的粒子层范围
     */
    @JvmStatic
    fun renderParticlePass(camera: Camera, partial: Float, pass: CParticleRenderPass) {
        if (!ready() || systems.isEmpty() || pass == CParticleRenderPass.NONE) return
        val replayScenePost = CooTerrainPipelineManager.cParticleForegroundReplayScenePost()
        if (replayScenePost != null && PostEffectFrameExecutor.supportsForegroundReplay()) {
            deferredTerrainForegroundFrame = renderFrameId
            deferredTerrainForegroundScenePost = replayScenePost
            return
        }
        CParticleRenderer.render(
            systems.values,
            RenderSystem.getModelViewMatrix(),
            RenderSystem.getProjectionMatrix(),
            camera,
            partial,
            pass
        )
    }

    /** 当前帧是否已把 CParticle 从原粒子 pass 延迟到任一 Mapping 后处理阶段。 */
    internal fun hasDeferredTerrainForeground(): Boolean {
        return deferredTerrainForegroundFrame == renderFrameId
    }

    /** 当前帧是否已把 CParticle 从原粒子 pass 延迟到指定 Mapping 后处理阶段。 */
    internal fun hasDeferredTerrainForeground(scenePost: Boolean): Boolean {
        return hasDeferredTerrainForeground() && deferredTerrainForegroundScenePost == scenePost
    }

    /** 在 Mapping 后按原 layer、混合和 deferred-depth 语义重放当前帧全部 CParticle。 */
    internal fun renderDeferredTerrainForeground(
        view: Matrix4f,
        proj: Matrix4f,
        partial: Float,
        scenePost: Boolean,
    ) {
        check(hasDeferredTerrainForeground(scenePost)) {
            "No deferred CParticle foreground is scheduled for this post stage"
        }
        CParticleRenderer.renderTerrainForeground(
            systems.values,
            view,
            proj,
            Minecraft.getInstance().gameRenderer.mainCamera,
            partial,
        )
        deferredTerrainForegroundFrame = -1L
    }

    /** 在 Terrain Mapping 合成前按当前帧矩阵生成全部可见 CParticle 的隔离覆盖蒙版。 */
    internal fun renderTerrainCoverageMask(view: Matrix4f, proj: Matrix4f, partial: Float): Boolean {
        if (!ready() || systems.isEmpty()) return true
        return CParticleRenderer.renderTerrainCoverageMask(
            systems.values,
            view,
            proj,
            Minecraft.getInstance().gameRenderer.mainCamera,
            partial,
        )
    }

    /** 每客户端 tick 调用 (渲染线程) */
    @JvmStatic
    fun tick() {
        if (!ready()) return
        currentTick++
        CParticleBlockCollisionGridManager.beginTick(Minecraft.getInstance().level, currentTick)
        try {
            if (systems.isEmpty()) return
            val toRemove = ArrayList<ManagedCParticleSystemKey>(0)
            for ((key, system) in systems) {
                system.tick()
                if (system.store.aliveCount > 0) {
                    lastNonEmptyTick[key] = currentTick
                }
                val idleTicks = currentTick - (lastNonEmptyTick[key] ?: currentTick)
                if (key in autoRelease && shouldReleaseAutoSystem(
                        system.store.aliveCount,
                        key in terminalAutoRelease,
                        idleTicks,
                    )
                ) {
                    toRemove.add(key)
                }
            }
            toRemove.forEach(::removeSystem)
        } finally {
            CParticleBlockCollisionGridManager.endTick()
        }
    }

    /** 保留给手动世界渲染调用；平台默认路径使用 [renderParticlePass]。 */
    @JvmStatic
    fun renderWorld(view: Matrix4f, proj: Matrix4f, camera: Camera, delta: DeltaTracker) {
        if (!ready() || systems.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        renderFrameId++
        val partial = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().runsNormally())
        CParticleRenderer.render(systems.values, view, proj, camera, partial)
    }

    /** 断线 / 换世界: 清空所有粒子与动态系统 */
    @JvmStatic
    fun clear() {
        CParticleBlockCollisionGridManager.clear()
        val it = systems.entries.iterator()
        while (it.hasNext()) {
            val (key, system) = it.next()
            if (key in autoRelease) {
                system.release()
                it.remove()
                lastNonEmptyTick.remove(key)
            } else {
                system.clearParticles()
            }
        }
        autoRelease.removeAll { it !in systems.keys }
        terminalAutoRelease.clear()
    }

    /** 资源重载: 图集 UV 会变, 清空贴图缓存; shader 程序由 registry 自动重建 */
    @JvmStatic
    fun onResourceReload() {
        CParticleSprites.clearCache()
    }

    /** 完全释放 (退出/调试) */
    @JvmStatic
    fun releaseAll() {
        systems.values.forEach { it.release() }
        systems.clear()
        lastNonEmptyTick.clear()
        autoRelease.clear()
        terminalAutoRelease.clear()
        CParticleRenderer.release()
        CParticleGpuSimulator.release()
        CParticleBlockCollisionGridManager.clear()
        CParticleSprites.release()
    }
}
