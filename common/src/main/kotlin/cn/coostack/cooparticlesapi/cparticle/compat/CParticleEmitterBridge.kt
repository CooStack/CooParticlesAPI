package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemReuseKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.resolveTextures
import cn.coostack.cooparticlesapi.cparticle.force.CParticleFluidResource
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResource
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.cparticle.force.CParticleTextureResource
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
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
 * @property capacityHint 当前批次的 CParticle 数量，只用于首次分配
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
     * 示例：容量写满时原地翻倍，不创建 `/1`、`/2` 等分段。
     * 禁止跨 emitter 或纹理 binding 复用该引用。
     *
     * @return 当前可写的 GPU 粒子 system
     */
    fun findAvailable(): CParticleSystem {
        val current = system?.takeUnless(CParticleSystem::released)
            ?: getOrCreateSystem().also { system = it }
        if (current.store.isFull()) {
            val nextCapacity = CParticleEmitterBridge.nextSystemCapacity(current.capacity, globalLimit)
            if (nextCapacity > current.capacity) current.growTo(nextCapacity)
        }
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
     * 取得当前分组的 system，不存在时按批次提示创建较小的初始容量。
     *
     * @return 当前分组唯一的 system
     */
    private fun getOrCreateSystem(): CParticleSystem {
        return CParticleSystemManager.getSystem(
            baseName,
            CParticleSystemMode.SIMULATED,
            layer,
            textureBindingKey,
            maskTextureBindingKey,
        ) ?: CParticleSystemManager.getOrCreateSystem(
            baseName,
            CParticleEmitterBridge.initialSystemCapacity(capacityHint, globalLimit),
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
            autoReleaseWhenEmpty = true,
            maskTextureBindingKey = maskTextureBindingKey,
        )
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
 * 需要 singleParticleAction、精确碰撞或碰撞事件、singleParticleDeathAction 重生，
 * 或非全局/relative 风场的粒子，应继续使用普通 [ControlableParticleData]。
 * 仅需近似完整方块碰撞时，可使用 [ControlableCParticleData.blockCollision]。
 */
object CParticleEmitterBridge {

    /** 保存 emitter 在一个 tick 内复用的 Force Command 快照。 */
    private class EmitterForceState {
        var tick: Int = Int.MIN_VALUE
        val snapshot = CParticleForceSink()
    }

    /**
     * 每个 emitter 按渲染层和纹理 binding 保存少量 system 游标。
     *
     * 示例：同一 emitter 的普通纹理和方块蒙版各自保留一个游标。
     * 禁止在 [finishEmitter] 后保留对应 UUID 的条目。
     */
    private val systemCursors = HashMap<UUID, ArrayList<CParticleEmitterSystemCursor>>()
    private val forceStates = HashMap<UUID, EmitterForceState>()

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
     * @param capacityHint 当前批次的 CParticle 数量，用于确定 system 的初始容量
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

        val p = CParticle.from(data).also { it.mass = emitter.mass.toFloat() }
        p.pos = pos
        val resolved = p.resolveTextures(pos)
        if (!resolved.isValid) return true
        if (!CParticleSystemManager.hasAvailableParticleCapacity()) return true
        val layer = CParticleRenderLayer.fromSheetName(data.getTextureSheet().toString())
        val state = ensureForceState(emitter)
        val system = findAvailableSystem(
            emitter,
            layer,
            resolved.base.bindingKey,
            resolved.mask?.bindingKey,
            capacityHint,
            state.snapshot,
        )
        system.setOriginIfEmpty(emitter.pos)

        // 新 system 在首次写入前补齐当前 tick 的共享 Force 快照。
        if (system.forcesSyncTick != emitter.tick) {
            applyForceSnapshot(system, state.snapshot, state.tick)
        }
        system.blockCollisionRange = CParticleSystemManager.normalizeBlockCollisionRange(
            emitter.cparticleBlockCollisionRange(),
        )
        // 粒子生成时已按 data.visibleRange 逐粒子剔除过; 整池剔除范围取最大见过的值
        if (data.visibleRange.toDouble() > system.visibleRange) {
            system.visibleRange = data.visibleRange.toDouble()
        }

        system.spawnResolved(p, resolved)
        return true
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
     * 示例：当前 system 写满后扩大其 VBO 和 CPU 槽位数组。
     * 禁止：扩容不能绕过全局粒子上限检查。
     *
     * @param emitter 当前客户端发射器
     * @param layer 粒子的渲染层
     * @param textureBindingKey 本批次使用的基础纹理绑定
     * @param maskTextureBindingKey 本批次使用的可选蒙版纹理绑定
     * @param capacityHint 首次创建 system 时使用的 CParticle 批次数量
     * @return 一个仍可写入的 SIMULATED system
     */
    private fun findAvailableSystem(
        emitter: ClassParticleEmitters,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        capacityHint: Int,
        forceSnapshot: CParticleForceSink,
    ): CParticleSystem {
        val globalLimit = CParticleSystemManager.particleCountLimit
        val cursors = systemCursors.getOrPut(emitter.uuid) { ArrayList(1) }
        val cursor = cursors.firstOrNull {
            it.matches(layer, textureBindingKey, maskTextureBindingKey)
        } ?: run {
            val baseName = "emitter/${emitter.uuid}/${layer.name.lowercase()}"
            val adopted = CParticleSystemManager.takeRetiredAutoSystem(
                baseName,
                CParticleSystemMode.SIMULATED,
                layer,
                textureBindingKey,
                maskTextureBindingKey,
            ) { candidate, reuseKey ->
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
            ).also(cursors::add)
        }
        cursor.capacityHint = capacityHint
        cursor.globalLimit = globalLimit
        return cursor.findAvailable()
    }

    /**
     * 标记一个 emitter 的 GPU systems 已不再接收新粒子。
     *
     * System 保留原 VBO 和存活粒子；完整兼容的新 emitter 可以接管。没有接管时，Manager 在粒子
     * 归零后的第一个 tick 释放它。
     *
     * @param emitter 已结束的 emitter
     */
    internal fun finishEmitter(emitter: ClassParticleEmitters) {
        val cursors = systemCursors.remove(emitter.uuid)
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
        systemCursors.clear()
        forceStates.clear()
    }

    /** 在 emitter 客户端 tick 中同步已经创建的 system，即使本 tick 没有生成新粒子。 */
    internal fun syncSystems(emitter: ClassParticleEmitters) {
        if (systemCursors[emitter.uuid].isNullOrEmpty()) return
        ensureForceState(emitter)
    }

    /**
     * 根据当前批次确定 system 的首次分配容量。
     *
     * @param batchParticleCount 当前批次的 CParticle 数量
     * @param globalLimit 当前全局存活数量上限
     * @return system 首次创建时使用的容量
     */
    internal fun initialSystemCapacity(batchParticleCount: Int, globalLimit: Int): Int {
        return batchParticleCount.coerceIn(MIN_SYSTEM_CAPACITY, 32_767)
            .coerceAtMost(globalLimit.coerceAtLeast(1))
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
        systemCursors[emitter.uuid]?.forEach { cursor ->
            cursor.syncState(state.snapshot, state.tick, blockCollisionRange)
        }
        return state
    }

    /**
     * 为退休 System 生成严格的 emitter 接管键。
     *
     * 资源 Force 只记录稳定资源声明和本地槽位，不触发 GL 资源解析。相同类、位置、碰撞范围和
     * Command 原始位全部一致时，旧粒子才允许继续接受新 emitter 的共享 Command。
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

    private const val MIN_SYSTEM_CAPACITY = 16_384
}
