package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticlePerfProbe
import cn.coostack.cooparticlesapi.cparticle.CParticleResolvedTextures
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleRespawnEngine
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemReuseKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.resolveTextures
import cn.coostack.cooparticlesapi.cparticle.force.CParticleFluidResource
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResource
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.cparticle.force.CParticleTextureResource
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.cparticle.path.CooPathCommandAbi
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDeathContext
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticlePreparedRespawns
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRespawnRequest
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import java.util.EnumMap
import java.util.UUID

/**
 * 保存一个 emitter 渲染分组的可扩容 system。
 *
 * 示例：相同 layer 和纹理 binding 的粒子共用一个实例。
 * 禁止在 binding 不同的粒子之间复用，否则会写入错误的 GPU system。
 *
 * @property baseName 该分组创建 system 时使用的名称前缀
 * @property layer 粒子渲染层
 * @property textureBindingKey 基础纹理 binding
 * @property maskTextureBindingKey 可选蒙版纹理 binding
 * @property capacityHint 当前批次的 CParticle 数量，用于批次开始时预留容量
 * @property globalLimit 当前全局存活数量上限
 */
private class CParticleEmitterSystemCursor(
    private val baseName: String,
    private val layer: CParticleRenderLayer,
    private val textureBindingKey: CParticleTextureBindingKey,
    private val maskTextureBindingKey: CParticleTextureBindingKey?,
    var capacityHint: Int,
    var globalLimit: Int,
    initialSystem: CParticleSystem? = null,
) {
    /** 当前分组唯一的 system；Manager 释放后在下一次生成时替换。 */
    private var system: CParticleSystem? = initialSystem
    /** 一个批次仅使用一次总数量提示，避免每生成一粒都重复追加整批容量。 */
    var reserveBatchCapacity = true

    /**
     * 判断粒子是否属于当前渲染分组。
     *
     * 示例：基础和蒙版 binding 都相同时返回 `true`。
     * 禁止只比较渲染层，纹理 binding 也是 system 键的一部分。
     *
     * @param layer 待匹配的渲染层
     * @param textureBindingKey 待匹配的基础纹理 binding
     * @param maskTextureBindingKey 待匹配的蒙版纹理 binding
     * @return 三项 system 键都相同时返回 `true`
     */
    fun matches(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): Boolean = this.layer == layer &&
        this.textureBindingKey == textureBindingKey &&
        this.maskTextureBindingKey == maskTextureBindingKey

    /**
     * 返回该渲染分组当前可写的 system。
     *
     * 示例：批次开始时一次预留空间，后继树也在根粒子写入前预留。
     * 禁止跨 emitter 或纹理 binding 复用该引用。
     *
     * @return 当前可写的 GPU 粒子 system
     */
    fun findAvailable(requiredSlots: Int): CParticleSystem {
        val current = system?.takeUnless(CParticleSystem::released)
            ?: getOrCreateSystem(requiredSlots).also { system = it }
        val additional = if (reserveBatchCapacity) maxOf(capacityHint, requiredSlots) else requiredSlots
        CParticleEmitterBridge.reserveSystemCapacity(current, additional, globalLimit)
        reserveBatchCapacity = false
        return current
    }

    /** 把 Force 快照和碰撞范围同步到当前分组的 system。 */
    fun syncState(snapshot: CParticleForceSink, tick: Int, blockCollisionRange: Int) {
        system?.takeUnless(CParticleSystem::released)?.let { current ->
            CParticleEmitterBridge.applyForceSnapshot(current, snapshot, tick)
            current.blockCollisionRange = blockCollisionRange
        }
    }

    /** 停止当前 emitter 写入，并把仍由 Manager 持有的 system 交给退休池。 */
    fun detachSystem(): CParticleSystem? {
        val current = system?.takeUnless(CParticleSystem::released)
        system = null
        return current
    }

    /**
     * 取得当前分组的 system，不存在时按批次提示与已知后继树确定初始容量。
     *
     * @return 当前分组唯一的 system
     */
    private fun getOrCreateSystem(requiredSlots: Int): CParticleSystem {
        return CParticleSystemManager.getSystem(
            baseName,
            CParticleSystemMode.SIMULATED,
            layer,
            textureBindingKey,
            maskTextureBindingKey,
        ) ?: CParticleSystemManager.getOrCreateSystem(
            baseName,
            CParticleEmitterBridge.initialSystemCapacity(maxOf(capacityHint, requiredSlots), globalLimit),
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
            autoReleaseWhenEmpty = true,
            maskTextureBindingKey = maskTextureBindingKey,
        )
    }
}

/**
 * Emitter 内 GPU system 的无分配分层索引。
 *
 * 同一 emitter 通常会连续生成大量相同纹理的粒子。分层保存 binding 映射后，
 * 出生热路径不需要对 cursor 线性扫描，也不会为每颗粒子创建组合 key。
 */
private class EmitterSystemCursorLookup {
    private val byLayer =
        EnumMap<CParticleRenderLayer, HashMap<CParticleTextureBindingKey, HashMap<CParticleTextureBindingKey?, CParticleEmitterSystemCursor>>>(
            CParticleRenderLayer::class.java,
        )

    fun get(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ): CParticleEmitterSystemCursor? {
        return byLayer[layer]?.get(textureBindingKey)?.get(maskTextureBindingKey)
    }

    fun put(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        cursor: CParticleEmitterSystemCursor,
    ) {
        byLayer
            .getOrPut(layer) { HashMap(2) }
            .getOrPut(textureBindingKey) { HashMap(2) }[maskTextureBindingKey] = cursor
    }
}

/**
 * 一个 emitter 当前批次的纹理解析缓存。
 *
 * 只有不依赖生成位置的来源才会进入这里；Block 来源仍然逐粒子解析，以保留
 * biome tint、亮度和模型位置语义。缓存不跨 batch 保留，避免 DYNAMIC data 在不同
 * 生成批次之间修改来源后读到旧结果。
 */
private class EmitterTextureResolveCache {
    private var generation = Int.MIN_VALUE
    private val values =
        HashMap<CParticleTextureSource, HashMap<CParticleTextureSource?, CParticleResolvedTextures>>()

    fun beginBatch() {
        generation = CParticleTextureResolver.generation
        values.clear()
    }

    fun resolve(
        particle: CParticle,
        position: Vec3,
    ): CParticleResolvedTextures {
        val currentGeneration = CParticleTextureResolver.generation
        if (generation != currentGeneration) {
            generation = currentGeneration
            values.clear()
        }
        val base = particle.effectiveTextureSource()
        val mask = particle.textureSource
        if (base is CParticleTextureSource.Block || mask is CParticleTextureSource.Block) {
            return particle.resolveTextures(position)
        }
        values[base]?.get(mask)?.let { return it }
        return particle.resolveTextures(position).also {
            values.getOrPut(base) { HashMap(1) }[mask] = it
        }
    }
}

/**
 * # CParticleEmitterBridge
 *
 * 发射器在客户端生成粒子时，把当前 [ControlableParticleData] 转成独立的 GPU 实例：
 * - 同一 emitter、渲染层、基础 binding 和蒙版 binding 共享 SIMULATED 系统；任一 binding 不同都会拆分
 * - 发射器内建物理 (gravity / airDensity / 全局风) 自动映射为 GPU 力场,
 *   与 `updatePhysics` 公式一致
 * - 附加运动通过 [ClassParticleEmitters.submitCParticleForces] 声明
 *   (内置 ParticleCommand 可用 [CParticleForce.fromCommand] 直接转换)
 * - 方块碰撞窗口通过 [ClassParticleEmitters.cparticleBlockCollisionRange] 声明
 * - 每份 data 使用自己的 effect SpriteSet，并可单独指定额外纹理蒙版
 *
 * 需要 singleParticleAction、精确碰撞或碰撞事件、旧版 singleParticleDeathAction，
 * 或非全局/relative 风场的粒子，应继续使用普通 [ControlableParticleData]。
 * 仅需近似完整方块碰撞时，可使用 [ControlableCParticleData.blockCollision]。
 * 通用死亡重生使用 [ClassParticleEmitters.deathCommand]；GPU 出生时固定后继，死亡时在 GPU 激活。
 * 只有 GPU 到普通粒子的显式转换才异步回读运动状态。
 */
object CParticleEmitterBridge {

    internal class ForceSnapshotSignature(
        bits: IntArray,
        resources: List<CParticleForceResource>,
        dropped: Int,
    ) {
        private var bits = bits.copyOf()
        private var mutableResources = ArrayList(resources)
        private var mutableDropped = dropped
        val resources: List<CParticleForceResource>
            get() = mutableResources
        val dropped: Int
            get() = mutableDropped

        override fun equals(other: Any?): Boolean =
            other is ForceSnapshotSignature &&
                mutableDropped == other.mutableDropped &&
                bits.contentEquals(other.bits) &&
                mutableResources == other.mutableResources

        override fun hashCode(): Int {
            var result = bits.contentHashCode()
            result = 31 * result + mutableResources.hashCode()
            result = 31 * result + mutableDropped
            return result
        }

        /**
         * 用复用的打包结果比较签名，避免每个 emitter 每 tick 创建 IntArray 和资源映射。
         */
        fun matches(
            packed: FloatArray,
            packedCount: Int,
            resources: List<CParticleForceResource>,
            dropped: Int,
        ): Boolean {
            if (mutableDropped != dropped || bits.size != packedCount || mutableResources != resources) return false
            for (index in 0 until packedCount) {
                if (bits[index] != packed[index].toRawBits()) return false
            }
            return true
        }

        /** 只在内容变化时更新已有签名对象，保持 force state 的长期零分配。 */
        fun update(
            packed: FloatArray,
            packedCount: Int,
            resources: List<CParticleForceResource>,
            dropped: Int,
        ) {
            if (bits.size != packedCount) bits = IntArray(packedCount)
            for (index in 0 until packedCount) bits[index] = packed[index].toRawBits()
            mutableResources.clear()
            mutableResources.addAll(resources)
            mutableDropped = dropped
        }
    }

    /** 保存 emitter 在一个 tick 内复用的 Force Command 快照。 */
    private class EmitterForceState {
        var tick: Int = Int.MIN_VALUE
        var revision: Int = 0
        var initialized = false
        var blockCollisionRange = Int.MIN_VALUE
        var signature: ForceSnapshotSignature? = null
        val snapshot = CParticleForceSink()
        val signaturePacked = FloatArray(ForceCommand.MAX_COMMANDS * ForceCommand.STRIDE)
        val signatureResources = ArrayList<CParticleForceResource>(8)
    }

    /**
     * 每个 emitter 按渲染层和纹理 binding 保存少量 system 游标。
     *
     * 示例：同一 emitter 的普通纹理和方块蒙版各自保留一个游标。
     * 禁止在 [finishEmitter] 后保留对应 UUID 的条目。
     */
    private val systemCursors = HashMap<UUID, ArrayList<CParticleEmitterSystemCursor>>()
    /**
     * 与 [systemCursors] 同步的直接索引，仅用于逐粒子出生热路径。
     *
     * ArrayList 仍保留作生命周期遍历；HashMap 避免每颗粒子都执行 firstOrNull + matches。
     */
    private val systemCursorLookup = HashMap<UUID, EmitterSystemCursorLookup>()
    private val textureResolveCaches = HashMap<UUID, EmitterTextureResolveCache>()
    private val forceStates = HashMap<UUID, EmitterForceState>()
    /**
     * STATIC GPU 粒子在写入 SoA 后不会再读取描述对象，因此每个 emitter 只需一个
     * 可复用转换对象。DYNAMIC 粒子仍在 spawnGpu 内独立创建并交给 store 持有。
     */
    private val staticParticleScratch = HashMap<UUID, CParticle>()

    /** 标记一次 genParticles 结果的开始；每个渲染分组只预留一次批次数量。 */
    internal fun beginBatch(emitterId: UUID) {
        systemCursors[emitterId]?.forEach { it.reserveBatchCapacity = true }
        textureResolveCaches.getOrPut(emitterId) { EmitterTextureResolveCache() }.beginBatch()
    }

    /** system 遍历完成后再创建后继，避免更改 Manager 正在遍历的系统集合。 */
    private val pendingRespawns = ArrayDeque<() -> Unit>()

    /** 当前死亡批次中新生成粒子的 system，仅在整批完成后上传一次。 */
    private val respawnUploads = LinkedHashSet<CParticleSystem>()

    /** 避免普通发射产生额外上传；仅在 Manager tick 末尾的死亡批次内置位。 */
    private var flushingRespawns = false

    /** 只执行当前批次，回调新增的事件不会在同一次 drain 中递归执行。 */
    internal fun flushRespawns() {
        val count = pendingRespawns.size
        flushingRespawns = true
        try {
            repeat(count) {
                val spawn = pendingRespawns.removeFirstOrNull() ?: return
                spawn()
            }
        } finally {
            flushingRespawns = false
            try {
                respawnUploads.forEach { it.uploadPendingSpawns() }
            } finally {
                respawnUploads.clear()
            }
        }
    }

    /** 换世界或关闭时丢弃尚未生成的后继。 */
    internal fun clearPendingRespawns() {
        pendingRespawns.clear()
        respawnUploads.clear()
    }

    /**
     * 尝试把一个粒子交给 GPU 系统.
     *
     * 示例：支持 GPU 时生成粒子；达到全局上限时直接丢弃本次生成请求。
     * 禁止：容量不足不能返回 `false`，否则调用方会生成 CPU 粒子。
     *
     * @param emitter 当前客户端发射器
     * @param world 当前客户端世界
     * @param pos 粒子的生成坐标
     * @param data 粒子数据
     * @param capacityHint 当前批次的 CParticle 数量，用于首次分配和批次预留
     * @return GPU 路径已处理时返回 `true`；无效纹理或达到全局容量时也会消费本次生成请求
     * @throws IllegalStateException CParticle manager 被关闭，或 GPU 渲染能力未完成探测时抛出
     */
    @JvmStatic
    fun trySpawn(
        emitter: ClassParticleEmitters,
        world: ClientLevel,
        pos: Vec3,
        data: ControlableCParticleData,
        capacityHint: Int,
    ): Boolean {
        check(CParticleSystemManager.enabled) {
            "[cparticle] CParticleSystemManager.enabled=false，拒绝把 GPU 粒子回退到 CPU"
        }
        CParticleCapabilities.detect()
        CParticleCapabilities.requireGpuParticleRendering()
        val command = emitter.deathCommand
        val prepared = if (!CParticleCapabilities.forceCpuSimulation &&
            command?.acceptsGeneration(data.respawnCount) == true
        ) ParticlePreparedRespawns(command, data, pos, emitter.pos) else null
        // 容量按整条 GPU 链预留；不足时不产生残缺的后继树，也不回退普通粒子。
        val required = 1 + (prepared?.nodes?.count { it.request.data is ControlableCParticleData } ?: 0)
        if (required > CParticleSystemManager.particleCountLimit - CParticleSystemManager.totalAlive()) return true
        val root = spawnGpu(emitter, pos, data, capacityHint, required) ?: return true
        val (system, slot) = root
        if (prepared != null && prepared.nodes.isNotEmpty()) {
            val allocated = arrayListOf(root)
            try {
                val gpuNodes = arrayOfNulls<Pair<CParticleSystem, Int>>(prepared.nodes.size)
                for ((index, node) in prepared.nodes.withIndex()) {
                    val child = node.request.data as? ControlableCParticleData ?: continue
                    val target = spawnGpu(emitter, node.referencePosition, child, capacityHint)
                    if (target == null) {
                        allocated.asReversed().forEach { (owner, targetSlot) ->
                            CParticleRespawnEngine.rollback(owner, targetSlot)
                        }
                        return true
                    }
                    allocated.add(target)
                    gpuNodes[index] = target
                }
                CParticleRespawnEngine.track(system, slot, waiting = false, command!!.includeManualRemoval)
                for (index in prepared.nodes.indices) {
                    val (target, childSlot) = gpuNodes[index] ?: continue
                    CParticleRespawnEngine.track(target, childSlot, waiting = true, command.includeManualRemoval)
                }
                val cpuChildren = LinkedHashMap<Int, MutableList<ParticleRespawnRequest>>()
                for ((index, node) in prepared.nodes.withIndex()) {
                    val parent = if (node.parent < 0) root else checkNotNull(gpuNodes[node.parent])
                    val child = gpuNodes[index]
                    if (child == null) cpuChildren.getOrPut(node.parent) { ArrayList() }.add(node.request)
                    else CParticleRespawnEngine.link(parent.first, parent.second, child.first, child.second, node.request)
                }
                for ((parentIndex, children) in cpuChildren) {
                    val parent = if (parentIndex < 0) root else checkNotNull(gpuNodes[parentIndex])
                    CParticleRespawnEngine.onCpuChildren(parent.first, parent.second) { death ->
                        pendingRespawns.addLast {
                            val requests = children.map { request ->
                                request.copy(
                                    data = request.data.clone().apply {
                                        respawnCount = request.data.respawnCount
                                        velocity += death.velocity * request.inheritVelocity
                                    },
                                    position = if (request.relativeToDeath) death.position + request.position else request.position,
                                    relativeToDeath = false, inheritVelocity = 0.0,
                                )
                            }
                            emitter.spawnPreparedDeathParticles(requests, world)
                        }
                    }
                }
            } catch (error: Throwable) {
                allocated.asReversed().forEach { (owner, targetSlot) -> CParticleRespawnEngine.rollback(owner, targetSlot) }
                throw error
            }
        } else if (CParticleCapabilities.forceCpuSimulation && command?.acceptsGeneration(data.respawnCount) == true) {
            val birthData = data.clone().apply { respawnCount = data.respawnCount }
            system.trackDeath(slot, system.store.generations[slot]) { death ->
                pendingRespawns.addLast {
                    emitter.spawnDeathParticles(command, ParticleDeathContext(
                        birthData, death.position, death.velocity, death.age, death.reason,
                        emitter.pos, data.respawnCount,
                    ), world)
                }
            }
        }
        return true
    }

    /** 只写入本次生命的静态配置，不递归执行死亡指令。 */
    private fun spawnGpu(
        emitter: ClassParticleEmitters, pos: Vec3, data: ControlableCParticleData, capacityHint: Int,
        requiredSlots: Int = 1,
    ): Pair<CParticleSystem, Int>? {
        // STATIC 粒子的描述只在出生时读取；复用一个 scratch 可避免百万粒子生成时
        // 为每颗粒子创建 CParticle、颜色和旋转向量。DYNAMIC 仍保留独立对象供 store 持有。
        val p = if (data.updateMode == CParticleUpdateMode.STATIC) {
            staticParticleScratch.getOrPut(emitter.uuid, ::CParticle).also { it.copyFrom(data) }
        } else {
            CParticle.from(data)
        }.also { it.mass = emitter.mass.toFloat() }
        p.pos = pos
        // 逐粒子的纹理解析与 SoA 写入分开计时：批量生成卡顿时这两段的表现完全不同。
        val resolved = CParticlePerfProbe.measure(CParticlePerfProbe.Stage.PARTICLE_RESOLVE_TEXTURES) {
            textureResolveCaches
                .getOrPut(emitter.uuid) { EmitterTextureResolveCache() }
                .resolve(p, pos)
        }
        if (!resolved.isValid) return null
        if (!CParticleSystemManager.hasAvailableParticleCapacity()) return null
        val layer = CParticleRenderLayer.fromSheetName(data.getTextureSheet().toString())
        val state = ensureForceState(emitter)
        val system = findAvailableSystem(
            emitter,
            layer,
            resolved.base.bindingKey,
            resolved.mask?.bindingKey,
            capacityHint,
            state.snapshot,
            requiredSlots,
        )
        system.setOriginIfEmpty(emitter.pos)

        // 新 system 在首次写入前补齐当前 tick 的共享 Force 快照。
        if (system.forcesSyncTick != state.revision) {
            applyForceSnapshot(system, state.snapshot, state.revision)
        }
        system.blockCollisionRange = CParticleSystemManager.normalizeBlockCollisionRange(
            emitter.cparticleBlockCollisionRange(),
        )
        // 粒子生成时已按 data.visibleRange 逐粒子剔除过; 整池剔除范围取最大见过的值
        if (data.visibleRange.toDouble() > system.visibleRange) {
            system.visibleRange = data.visibleRange.toDouble()
        }

        val slot = CParticlePerfProbe.measure(CParticlePerfProbe.Stage.PARTICLE_SPAWN_BATCH) {
            system.spawnResolved(p, resolved)
        }
        if (slot >= 0 && flushingRespawns) respawnUploads.add(system)
        return if (slot >= 0) system to slot else null
    }

    /**
     * 兼容直接调用桥接器的旧入口；没有批次信息时使用最小 system 容量。
     *
     * @param emitter 当前客户端发射器
     * @param world 当前客户端世界
     * @param pos 粒子的生成坐标
     * @param data 粒子数据
     * @return GPU 路径已处理时返回 `true`；无效纹理或达到全局容量时也会消费本次生成请求
     * @throws IllegalStateException CParticle manager 被关闭，或 GPU 渲染能力未完成探测时抛出
     */
    @JvmStatic
    fun trySpawn(
        emitter: ClassParticleEmitters,
        world: ClientLevel,
        pos: Vec3,
        data: ControlableCParticleData,
    ): Boolean = trySpawn(emitter, world, pos, data, MIN_SYSTEM_CAPACITY)

    /**
     * 返回仍有空槽位的 emitter system；容量不足时原地扩容。
     *
     * 示例：在新批次或已知后继树写入前扩大 VBO 和 CPU 槽位数组。
     * 禁止：扩容不能绕过全局粒子上限检查。
     *
     * @param emitter 当前客户端发射器
     * @param layer 粒子的渲染层
     * @param textureBindingKey 本批次使用的基础纹理绑定
     * @param maskTextureBindingKey 本批次使用的可选蒙版纹理绑定
     * @param capacityHint 当前 CParticle 批次数量
     * @param requiredSlots 当前根粒子及其已求值 GPU 后继树占用的槽位数
     * @return 一个仍可写入的 SIMULATED system
     */
    private fun findAvailableSystem(
        emitter: ClassParticleEmitters,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        capacityHint: Int,
        forceSnapshot: CParticleForceSink,
        requiredSlots: Int,
    ): CParticleSystem {
        val globalLimit = CParticleSystemManager.particleCountLimit
        val cursors = systemCursors.getOrPut(emitter.uuid) { ArrayList(1) }
        val lookup = systemCursorLookup.getOrPut(emitter.uuid) { EmitterSystemCursorLookup() }
        val cursor = lookup.get(layer, textureBindingKey, maskTextureBindingKey) ?: run {
            val baseName = "emitter/${emitter.uuid}/${layer.name.lowercase()}"
            val adopted = CParticleSystemManager.takeRetiredAutoSystem(
                baseName,
                CParticleSystemMode.SIMULATED,
                layer,
                textureBindingKey,
                maskTextureBindingKey,
            ) { candidate, reuseKey ->
                // 移动拖尾通常不可能接管仍有粒子的旧池，先用廉价字段排除，再打包完整命令。
                reuseKey.emitterType == emitter.getEmittersID() &&
                    reuseKey.emitterPosition == emitter.pos &&
                    buildSystemReuseKey(emitter, forceSnapshot, candidate.origin) == reuseKey
            }
            val managedName = adopted?.managedName ?: CParticleSystemManager.availableAutoSystemName(
                baseName,
                CParticleSystemMode.SIMULATED,
                layer,
                textureBindingKey,
                maskTextureBindingKey,
            )
            CParticleEmitterSystemCursor(
                managedName,
                layer,
                textureBindingKey,
                maskTextureBindingKey,
                capacityHint,
                globalLimit,
                adopted?.system,
            ).also {
                cursors.add(it)
                lookup.put(layer, textureBindingKey, maskTextureBindingKey, it)
            }
        }
        cursor.capacityHint = capacityHint
        cursor.globalLimit = globalLimit
        return cursor.findAvailable(requiredSlots)
    }

    /**
     * 标记一个 emitter 的 GPU systems 已不再接收新粒子。
     *
     * System 保留原 VBO 和存活粒子；完整兼容的新 emitter 可以接管。没有接管时，Manager 在粒子
     * 归零后的第一个 tick 缓存小空池或释放大池。
     *
     * @param emitter 已结束的 emitter
     */
    internal fun finishEmitter(emitter: ClassParticleEmitters) {
        val cursors = systemCursors.remove(emitter.uuid)
        systemCursorLookup.remove(emitter.uuid)
        textureResolveCaches.remove(emitter.uuid)
        staticParticleScratch.remove(emitter.uuid)
        val state = forceStates.remove(emitter.uuid)
        if (cursors.isNullOrEmpty()) return
        val snapshot = state?.snapshot ?: CParticleForceSink().also { target ->
            buildForceSnapshot(target, emitter)
        }
        for (cursor in cursors) {
            val system = cursor.detachSystem() ?: continue
            CParticleSystemManager.retireAutoSystem(
                system,
                buildSystemReuseKey(emitter, snapshot, system.origin),
            )
        }
    }

    /** 清除 bridge 持有的 emitter 游标和快照；System 生命周期由 Manager 统一处理。 */
    internal fun clear() {
        clearPendingRespawns()
        systemCursors.clear()
        systemCursorLookup.clear()
        textureResolveCaches.clear()
        staticParticleScratch.clear()
        forceStates.clear()
    }

    /** 在 emitter 客户端 tick 中同步已经创建的 system，即使本 tick 没有生成新粒子。 */
    internal fun syncSystems(emitter: ClassParticleEmitters) {
        if (systemCursors[emitter.uuid].isNullOrEmpty()) return
        ensureForceState(emitter)
    }

    /**
     * 根据当前批次确定 system 的首次分配容量，按二次幂预留空间。
     * 少量拖尾不再分配万级槽位，大批次直接预留足够容量，后续仍按几何级数增长。
     *
     * @param batchParticleCount 当前批次的 CParticle 数量
     * @param globalLimit 当前全局存活数量上限
     * @return system 首次创建时使用的容量
     */
    internal fun initialSystemCapacity(batchParticleCount: Int, globalLimit: Int): Int {
        val maximum = globalLimit.coerceAtLeast(1).toLong()
        val target = batchParticleCount.toLong().coerceAtLeast(MIN_SYSTEM_CAPACITY.toLong()).coerceAtMost(maximum)
        var capacity = MIN_SYSTEM_CAPACITY.toLong()
        while (capacity < target) capacity *= 2L
        return capacity.coerceAtMost(maximum).toInt()
    }

    /**
     * 在写入前为已知批次或后继树预留容量，保留当前粒子的 CPU/GPU 状态。
     * 二次幂分配与至少翻倍结合，未知大小的连续生成仍保持几何增长。
     *
     * @param system 当前渲染分组的 system
     * @param additionalParticles 尚未写入的粒子数量估计，包含已求值的 GPU 后继
     * @param globalLimit 当前全局存活数量上限
     */
    internal fun reserveSystemCapacity(system: CParticleSystem, additionalParticles: Int, globalLimit: Int) {
        val target = (system.store.aliveCount.toLong() + additionalParticles.coerceAtLeast(1))
            .coerceAtMost(globalLimit.coerceAtLeast(1).toLong())
        if (target <= system.capacity) return
        val capacity = maxOf(
            nextSystemCapacity(system.capacity, globalLimit),
            initialSystemCapacity(target.toInt(), globalLimit),
        )
        if (capacity > system.capacity) system.growTo(capacity)
    }

    /**
     * 返回同一 system 的下一次几何扩容容量。
     *
     * @param currentCapacity 当前槽位容量
     * @param globalLimit 当前全局存活数量上限
     * @return 翻倍后且不超过全局上限的容量；无法继续增长时返回原值
     */
    internal fun nextSystemCapacity(currentCapacity: Int, globalLimit: Int): Int {
        require(currentCapacity > 0) { "currentCapacity must be positive: $currentCapacity" }
        val maximum = globalLimit.coerceAtLeast(1)
        if (currentCapacity >= maximum) return currentCapacity
        return (currentCapacity.toLong() * 2L)
            .coerceAtMost(maximum.toLong())
            .toInt()
    }

    /** 将发射器内建物理和声明式力同步到目标 system，供真实桥接链路测试复用。 */
    internal fun syncForces(system: CParticleSystem, emitter: ClassParticleEmitters) {
        val target = CParticleForceSink()
        buildForceSnapshot(target, emitter)
        applyForceSnapshot(system, target, emitter.tick)
    }

    /** 构建一个 emitter 在当前 tick 使用的共享 Force Command 快照。 */
    private fun buildForceSnapshot(target: CParticleForceSink, emitter: ClassParticleEmitters) {
        target.clear()
        // 与 ClassParticleEmitters.updatePhysics 相同的三项内建物理
        if (emitter.gravity != 0.0) {
            target.submit(CParticleForce.Gravity(emitter.gravity))
        }
        if (emitter.airDensity > 0.0) {
            target.submit(CParticleForce.EnvDrag(emitter.airDensity))
        }
        val wind = emitter.wind
        if (wind is GlobalWindDirection && !wind.relative && wind.direction.lengthSqr() > 1e-12) {
            target.submit(CParticleForce.Wind({ wind.direction }, emitter.airDensity.coerceAtLeast(1e-4)))
        }
        emitter.submitCParticleForces(target)
    }

    /** 首次进入新 tick 时只构建一次快照，并同步到该 emitter 的全部 system。 */
    private fun ensureForceState(emitter: ClassParticleEmitters): EmitterForceState {
        val state = forceStates.getOrPut(emitter.uuid, ::EmitterForceState)
        if (state.tick == emitter.tick) return state
        state.tick = emitter.tick
        buildForceSnapshot(state.snapshot, emitter)
        val blockCollisionRange = CParticleSystemManager.normalizeBlockCollisionRange(
            emitter.cparticleBlockCollisionRange(),
        )
        val packedCount = packForceSnapshotSignature(
            state.snapshot,
            emitter.pos,
            state.signaturePacked,
            state.signatureResources,
        )
        // 自定义 submitCParticleForces 可能每 tick 创建新的 Force 对象，因此不能比较对象
        // 引用；接管键使用打包后的实际参数和动态中心，能安全识别“内容未变”的快照。
        if (state.initialized &&
            state.signature?.matches(
                state.signaturePacked,
                packedCount,
                state.signatureResources,
                state.snapshot.dropped,
            ) == true &&
            blockCollisionRange == state.blockCollisionRange
        ) {
            return state
        }
        state.initialized = true
        state.revision++
        state.blockCollisionRange = blockCollisionRange
        val previousSignature = state.signature
        if (previousSignature == null) {
            state.signature = ForceSnapshotSignature(
                IntArray(packedCount) { state.signaturePacked[it].toRawBits() },
                state.signatureResources,
                state.snapshot.dropped,
            )
        } else {
            previousSignature.update(
                state.signaturePacked,
                packedCount,
                state.signatureResources,
                state.snapshot.dropped,
            )
        }
        systemCursors[emitter.uuid]?.forEach { cursor ->
            cursor.syncState(state.snapshot, state.revision, blockCollisionRange)
        }
        return state
    }

    /**
     * 将 Force snapshot 写入可复用缓冲，供每 tick 的内容比较使用。
     *
     * 资源槽位只在本次 scratch 列表中做线性查找；资源型 Force 数量通常很少，
     * 这样可以消除每个 emitter 的 LinkedHashMap/FloatArray/IntArray 分配。
     */
    private fun packForceSnapshotSignature(
        snapshot: CParticleForceSink,
        origin: Vec3,
        packed: FloatArray,
        resources: ArrayList<CParticleForceResource>,
    ): Int {
        resources.clear()
        var index = 0
        snapshot.commands().forEach { command ->
            val base = index * ForceCommand.STRIDE
            when (val force = command.force) {
                is CParticleForce.Texture -> {
                    val slot = resources.indexOf(force.resource).let { existing ->
                        if (existing >= 0) existing else resources.apply {
                            add(force.resource)
                        }.lastIndex
                    }
                    command.pack(packed, base, origin, slot)
                }
                is CParticleForce.FluidFlow -> {
                    val slot = resources.indexOf(force.resource).let { existing ->
                        if (existing >= 0) existing else resources.apply {
                            add(force.resource)
                        }.lastIndex
                    }
                    command.pack(packed, base, origin, slot)
                }
                is CParticleForce.Path -> {
                    command.packHeader(packed, base)
                    val payload = base + CooPathCommandAbi.FORCE_PAYLOAD_OFFSET
                    packed[payload + CooPathCommandAbi.P_SLOT] = Float.fromBits(force.path.slot)
                    packed[payload + CooPathCommandAbi.P_LAYER_VERSION] =
                        Float.fromBits(force.path.revision)
                }
                else -> command.pack(packed, base, origin)
            }
            index++
        }
        return index * ForceCommand.STRIDE
    }

    /**
     * 为退休 System 生成严格的 emitter 接管键。
     *
     * 资源 Force 只记录稳定资源声明和本地槽位，不触发 GL 资源解析。相同类、位置、碰撞范围和
     * Command 原始位全部一致时，旧粒子才允许继续接受新 emitter 的共享 Command。
     *
     * 路径约束**不能**走 [ForceCommand.pack]：它的参数需要打包阶段的图层槽位与重建版本，
     * 而本方法只用于比较两次快照是否等价。这里改用槽位号与图层版本参与比较，
     * 两者都变化时接管键自然不相等，语义与其它 Force 的位比较一致。
     */
    internal fun buildSystemReuseKey(
        emitter: ClassParticleEmitters,
        snapshot: CParticleForceSink,
        origin: Vec3,
    ): CParticleSystemReuseKey {
        val commands = snapshot.commands()
        val packed = FloatArray(commands.size * ForceCommand.STRIDE)
        val textureSlots = LinkedHashMap<CParticleTextureResource, Int>()
        val fluidSlots = LinkedHashMap<CParticleFluidResource, Int>()
        val resources = ArrayList<CParticleForceResource>()
        commands.forEachIndexed { index, command ->
            val base = index * ForceCommand.STRIDE
            when (val force = command.force) {
                is CParticleForce.Texture -> {
                    val slot = textureSlots.getOrPut(force.resource) {
                        resources.add(force.resource)
                        textureSlots.size
                    }
                    command.pack(packed, base, origin, slot)
                }

                is CParticleForce.FluidFlow -> {
                    val slot = fluidSlots.getOrPut(force.resource) {
                        resources.add(force.resource)
                        fluidSlots.size
                    }
                    command.pack(packed, base, origin, slot)
                }

                is CParticleForce.Path -> {
                    command.packHeader(packed, base)
                    val payload = base + CooPathCommandAbi.FORCE_PAYLOAD_OFFSET
                    // 与 CParticlePathCommandPacker 写入实际命令时相同的两个字段：槽位号与图层版本。
                    // 两者都相同时，旧粒子的路径命令在新 emitter 下解析到同一条路径，接管才安全。
                    packed[payload + CooPathCommandAbi.P_SLOT] = Float.fromBits(force.path.slot)
                    packed[payload + CooPathCommandAbi.P_LAYER_VERSION] =
                        Float.fromBits(force.path.revision)
                }

                else -> command.pack(packed, base, origin)
            }
        }
        return CParticleSystemReuseKey(
            emitter.getEmittersID(),
            emitter.pos,
            CParticleSystemManager.normalizeBlockCollisionRange(emitter.cparticleBlockCollisionRange()),
            snapshot.dropped,
            IntArray(packed.size) { packed[it].toRawBits() },
            resources,
        )
    }

    /** 仅比较 Force 快照内容，供可变换 emitter 跳过重复同步。 */
    internal fun buildForceSnapshotSignature(
        snapshot: CParticleForceSink,
        origin: Vec3,
    ): ForceSnapshotSignature {
        val commands = snapshot.commands()
        val packed = FloatArray(commands.size * ForceCommand.STRIDE)
        val textureSlots = LinkedHashMap<CParticleTextureResource, Int>()
        val fluidSlots = LinkedHashMap<CParticleFluidResource, Int>()
        val resources = ArrayList<CParticleForceResource>()
        commands.forEachIndexed { index, command ->
            val base = index * ForceCommand.STRIDE
            when (val force = command.force) {
                is CParticleForce.Texture -> {
                    val slot = textureSlots.getOrPut(force.resource) {
                        resources.add(force.resource)
                        textureSlots.size
                    }
                    command.pack(packed, base, origin, slot)
                }
                is CParticleForce.FluidFlow -> {
                    val slot = fluidSlots.getOrPut(force.resource) {
                        resources.add(force.resource)
                        fluidSlots.size
                    }
                    command.pack(packed, base, origin, slot)
                }
                is CParticleForce.Path -> {
                    command.packHeader(packed, base)
                    val payload = base + CooPathCommandAbi.FORCE_PAYLOAD_OFFSET
                    packed[payload + CooPathCommandAbi.P_SLOT] = Float.fromBits(force.path.slot)
                    packed[payload + CooPathCommandAbi.P_LAYER_VERSION] =
                        Float.fromBits(force.path.revision)
                }
                else -> command.pack(packed, base, origin)
            }
        }
        return ForceSnapshotSignature(
            IntArray(packed.size) { packed[it].toRawBits() },
            resources,
            snapshot.dropped,
        )
    }

    /**
     * 把共享快照写入一个 system。
     *
     * 快照中的 [ForceCommand] 对象由同一 emitter 的
     * 全部 system 共用；system 只按自己的坐标空间打包，不会把 Command 展开到粒子实例。
     */
    internal fun applyForceSnapshot(
        system: CParticleSystem,
        snapshot: CParticleForceSink,
        tick: Int,
    ) {
        system.forces.clear()
        system.forceSink.replaceWith(snapshot)
        system.forcesSyncTick = tick
    }

    /** 小批次初始化及无批次提示的旧入口共用的最低槽位容量。 */
    private const val MIN_SYSTEM_CAPACITY = 256
}
