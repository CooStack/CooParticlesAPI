package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleSelector
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceResourceTable
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGridManager
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathCommandPacker
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.render.CParticleGlBuffer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleMetadataGlBuffer
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleCommandGlBuffer
import cn.coostack.cooparticlesapi.cparticle.storage.CParticlePathEndBuffer
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleDeathTracker
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.concurrent.atomic.AtomicInteger
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * # CParticleSystem — 一个 GPU 粒子池
 *
 * 一个系统 = 一份可扩容的 SoA 存储 + 一个 GL 实例缓冲 + 一个渲染层 + 一组力场.
 * 每帧一次 instanced draw；每 tick 使用 GL43 compute 模拟，或由调用方显式选择 CPU 并行模拟。
 *
 * 两种模式:
 * - [CParticleSystemMode.SIMULATED]: 发射器语义 — 粒子生成后由力场驱动, 不可单独控制
 *   (对应 "emitter 是数据源")
 * - [CParticleSystemMode.SCRIPTED]: composition 语义 — CPU 持有位置权威,
 *   通过句柄 teleport/rotate, 或整组 groupTransform 变换 (对应 "composition 是显示")
 */
class CParticleSystem(
    var name: String,
    capacity: Int,
    val layer: CParticleRenderLayer,
    val mode: CParticleSystemMode,
    /**
     * 本系统所有实例共用的基础纹理绑定。
     *
     * 示例：同一方块图集系统可混合多种 BlockState。
     * 禁止：粒子生成后不能把槽位改到另一 binding。
     */
    val textureBindingKey: CParticleTextureBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
) {
    /** Manager 在 emitter 接管后同步新的逻辑名称；不改变粒子、VBO 或 sourceId。 */
    internal fun renameForManager(newName: String) {
        name = newName
    }

    /** 构造阶段记录的蒙版 binding。示例：方块蒙版使用 `BLOCK_ATLAS`。禁止：初始化后不能改动。 */
    private var configuredMaskTextureBindingKey: CParticleTextureBindingKey? = null

    /**
     * 本 system 所有实例共用的可选蒙版纹理 binding。
     *
     * 示例：粒子图集基础纹理可以配 [CParticleTextureBindingKey.BLOCK_ATLAS] 蒙版。
     * 禁止：生成后不能把槽位切到另一蒙版图集。
     */
    val maskTextureBindingKey: CParticleTextureBindingKey?
        get() = configuredMaskTextureBindingKey

    /**
     * 创建同时固定基础纹理和蒙版纹理 binding 的粒子系统。
     *
     * 示例：`CParticleSystem(name, capacity, layer, mode, PARTICLE_ATLAS, BLOCK_ATLAS)`。
     * 禁止：蒙版 binding 不能在系统存活期间改变。
     *
     * @param name 系统逻辑名称
     * @param capacity 最大槽位数
     * @param layer 混合与深度状态
     * @param mode 更新模式
     * @param textureBindingKey 基础纹理 binding
     * @param maskTextureBindingKey 可选蒙版纹理 binding
     */
    constructor(
        name: String,
        capacity: Int,
        layer: CParticleRenderLayer,
        mode: CParticleSystemMode,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
    ) : this(name, capacity, layer, mode, textureBindingKey) {
        configuredMaskTextureBindingKey = maskTextureBindingKey
    }

    /**
     * 该 system 的 CPU 槽位存储，所有存活槽位计入全局 GPU 粒子上限。
     *
     * 示例：`store.spawn(...)` 仍会申请全局额度。
     * 禁止：不能用公开 store 入口绕过 [CParticleSystemManager.particleCountLimit]。
     */
    val store = CParticleStore.globallyCounted(capacity)

    /** 当前槽位容量；emitter system 写满后会按几何级数增长。 */
    val capacity: Int
        get() = store.capacity

    val glBuffer = CParticleGlBuffer(capacity)
    val metadataGlBuffer = CParticleMetadataGlBuffer(capacity)
    internal val commandGlBuffer = CParticleCommandGlBuffer()
    internal val forceResourceTable = CParticleForceResourceTable()

    /**
     * 路径约束“到达后消失”的 GPU → CPU 结束通知通道。
     *
     * 容量按 [PATH_END_RECORD_CAPACITY] 固定：列表只记录结束槽位号，溢出时计数器仍然增长，
     * CPU 会发现漏收并做一次兜底扫描，因此不需要按粒子容量分配。
     */
    internal val pathEndBuffer = CParticlePathEndBuffer(PATH_END_RECORD_CAPACITY)

    /** 当前死亡批次共享的只读 GPU 映射，未发生需捕获的死亡时保持为空。 */
    private var deathReadback: ByteBuffer? = null

    /** GPU 完成模拟后的回收窗口；窗口内所有死亡共用一次映射。 */
    private var collectingGpuDeaths = false

    /** 为有效槽位注册单次死亡回调；槽位世代失效时拒绝绑定。 */
    internal fun trackDeath(slot: Int, generation: Int, action: (CParticleDeathState) -> Unit) {
        check(checkHandle(slot, generation)) { "Cannot track an expired particle handle" }
        val tracker = store.deathTracker ?: CParticleDeathTracker(::captureDeathState).also {
            store.deathTracker = it
        }
        tracker.track(slot, action)
    }

    /** 捕获已回收但尚未复用的槽位；GPU 模式不读取 CPU 中过期的位置和速度。 */
    private fun captureDeathState(slot: Int, reason: RemoveReason): CParticleDeathState {
        val gpu = mode == CParticleSystemMode.SIMULATED && gpuSimulationSelected &&
            glBuffer.initialized && !store.isPendingSpawn(slot)
        try {
            val mapped = if (gpu) deathReadback ?: glBuffer.mapDeathReadback().also {
                deathReadback = it
            } else null
            val base = slot * CParticleStore.STRIDE
            fun value(offset: Int): Float = mapped?.getFloat((base + offset) * Float.SIZE_BYTES)
                ?: store.data[base + offset]
            val position = Vector3f(value(0), value(1), value(2))
            val velocity = Vector3f(
                value(CParticleStore.OFF_VEL), value(CParticleStore.OFF_VEL + 1),
                value(CParticleStore.OFF_VEL + 2),
            )
            // 槽位是相对 system 原点的局部坐标；回调始终获得世界坐标。
            groupTransform.transformPosition(position)
            groupTransform.transformDirection(velocity)
            return CParticleDeathState(
                origin.add(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
                Vec3(velocity.x.toDouble(), velocity.y.toDouble(), velocity.z.toDouble()),
                if (gpu) value(CParticleStore.OFF_AGE).toInt() else store.ages[slot],
                reason,
            )
        } finally {
            if (!collectingGpuDeaths) closeDeathReadback()
        }
    }

    private fun closeDeathReadback() {
        if (deathReadback == null) return
        deathReadback = null
        glBuffer.unmapDeathReadback()
    }

    /** 本 tick 打包路径命令时使用的图层重建版本。 */
    private var currentPathLayerVersion = 0

    /** 本 system 的运行时来源 ID；同一兼容分组的存活粒子共用该值。 */
    val sourceId: Int = nextSourceId()

    /**
     * 提前扩大 system，保留 CPU 账本、已有空闲槽和 GPU 模拟结果。
     *
     * @param newCapacity 新槽位容量，必须大于当前容量
     */
    internal fun growTo(newCapacity: Int) {
        check(!released) { "released CParticleSystem cannot grow: $name" }
        require(newCapacity > capacity) {
            "newCapacity must be greater than capacity: $newCapacity <= $capacity"
        }
        // GPU 复制与 CPU 数组复制是两个独立的成本来源，分开计时才能定位峰值。
        CParticlePerfProbe.measure(CParticlePerfProbe.Stage.SYSTEM_GROW_GPU) {
            glBuffer.growTo(newCapacity)
            metadataGlBuffer.growTo(newCapacity)
        }
        CParticlePerfProbe.measure(CParticlePerfProbe.Stage.SYSTEM_GROW_CPU) {
            store.growTo(newCapacity)
        }
    }

    /**
     * 系统原点 (双精度): 粒子位置以它为基准存 float,
     * 避免远坐标 float 精度抖动. 池为空时可自动重定位.
     */
    var origin: Vec3 = Vec3.ZERO
        private set

    /** 力场列表 (SIMULATED 模式生效) */
    val forces = ArrayList<CParticleForce>()

    /** 所有 Force 的统一 Command sink；bridge 只在 emitter tick 边界更新一次快照。 */
    internal val forceSink = CParticleForceSink(ForceCommand.MAX_COMMANDS)
    private var metadataGpuSynchronized = false

    /**
     * 自适应模拟路由是否已经切换到 GPU。
     *
     * 默认直接走 GPU；只有调用方显式设置小池 CPU 阈值时，才允许尚未切换过 GPU 的小池
     * 走 CPU SoA。切换到 GPU 后保持粘滞，避免在两个后端之间搬运状态。
     */
    private var gpuSimulationSelected = false

    private fun shouldUseGpuSimulation(prepared: Boolean): Boolean {
        if (CParticleCapabilities.forceCpuSimulation) return false
        if (prepared || gpuSimulationSelected) return true
        if (CParticleSystemManager.shouldPreferGpuForSmallSystems() ||
            store.activeSlotCount > CParticleSystemManager.smallSystemCpuThreshold
        ) {
            gpuSimulationSelected = true
            return true
        }
        return false
    }

    /**
     * 预判本 tick 是否会进入 GPU 模拟。
     *
     * Manager 用它决定是否需要开启跨 system 的 GL 批次；不能把所有未强制 CPU
     * 的 system 都当作 GPU system，否则全是小池时仍会产生无意义的 GL 状态读写。
     */
    internal fun willUseGpuSimulation(): Boolean {
        if (mode != CParticleSystemMode.SIMULATED || CParticleCapabilities.forceCpuSimulation) return false
        if (canSkipGpuDispatch()) return false
        return gpuSimulationSelected ||
            CParticleRespawnEngine.owns(this) ||
            CParticleSystemManager.shouldPreferGpuForSmallSystems() ||
            store.activeSlotCount > CParticleSystemManager.smallSystemCpuThreshold
    }

    /**
     * 判断当前 system 是否只包含静态零运动粒子，可以完全跳过 compute dispatch。
     * Manager 用它避免在整批都是 no-op system 时仍保存/恢复一套 GL batch 状态。
     */
    internal fun canSkipGpuDispatch(): Boolean =
        gpuNoopSimulation &&
            !CParticleRespawnEngine.owns(this) &&
            forces.isEmpty() &&
            forceSink.size == 0 &&
            !transformsSimulatedParticleSpace &&
            store.blockCollisionCount == 0 &&
            store.activeSlotCount > 0

    /** 一旦 system 曾经需要真实模拟，就不能在本生命周期内退回 no-op。 */
    internal fun disableGpuNoopSimulation() {
        gpuNoopSimulation = false
    }

    /** emitter 桥接: 上次同步力场和视觉配置的 emitter tick (避免同 tick 重复重建) */
    var forcesSyncTick = Int.MIN_VALUE

    /** 速度上限 (对应 ControlableParticleData.speedLimit) */
    var speedLimit = 32F

    /** Emitter 粒子相对 system 原点的方块碰撞保证范围。 */
    internal var blockCollisionRange = CParticleSystemManager.DEFAULT_BLOCK_COLLISION_RANGE
        set(value) {
            field = CParticleSystemManager.normalizeBlockCollisionRange(value)
        }

    /** 透明度生命周期曲线 (null = 恒定) */
    var alphaCurve: CParticleCurve? = null

    /**
     * 系统内所有粒子的生命周期等比缩放倍率曲线。
     *
     * 示例：`scaleCurve = CParticleCurve.linear(1F, 0F)` 会让粒子逐渐缩小。
     * 禁止：不要用它设置粒子生成时的基础宽高。
     */
    var scaleCurve: CParticleCurve? = null

    /** 颜色生命周期倍率曲线 (null = 保持实例颜色) */
    var colorCurve: CParticleColorCurve? = null

    /** 大于 0 时，alpha/scale/color 曲线按系统 tick 循环，不再使用粒子生命周期进度。 */
    var curveCycleTicks = 0F

    /** 大于 0 时，颜色在指定 tick 周期内完成一次色相循环。 */
    var colorCycleTicks = 0F

    /** 颜色沿粒子绕系统原点的角度分布多少个循环。 */
    var colorCycleSpatialScale = 0F

    internal var visualTransition: CParticleVisualTransition? = null
        private set

    internal var alphaTransition: CParticleVisualTransition? = null
        private set

    /**
     * 施加于粒子原点相对坐标的整组变换。
     * SCRIPTED system 用它控制整组显示；可变换 emitter 的专属 SIMULATED system 也会用它管理局部空间。
     */
    val groupTransform = Matrix4f()

    /** 让 SIMULATED 粒子的模拟坐标和顶点几何都经过整组矩阵；仅供可变换 emitter 使用。 */
    internal var transformsSimulatedParticleSpace = false
        set(value) {
            if (value) disableGpuNoopSimulation()
            field = value
        }

    /** 渲染用的前后 tick 整组变换，由 shader 按 partial tick 插值。 */
    internal val previousGroupTransform = Matrix4f()
    internal val currentGroupTransform = Matrix4f()

    /** 可见范围 (以 origin 为球心的粗剔除; 只影响绘制, 不影响模拟) */
    var visibleRange = 256.0

    /** 本系统经历的 tick 数 */
    var tickCount = 0
        private set

    /** scripted 模式: prev 收敛计数 (写入后需 1 tick 让 prev==cur) */
    private var settleTicks = 0

    /** 旧 1..9 Force 的兼容 uniform payload；仅用于传输快路径。 */
    private val packedForces = FloatArray(CParticleForce.MAX_FORCES * CParticleForce.STRIDE)
    private val packedCommands = FloatArray(ForceCommand.MAX_COMMANDS * ForceCommand.STRIDE)
    private var packedCommandsNeedMetadata = false
    /** emitter Force snapshot 未变化时复用已打包的 GPU payload，避免每个 system 每 tick 重复编码。 */
    private var packedPayloadRevision = Int.MIN_VALUE
    private var packedPayloadPathRevision = Int.MIN_VALUE
    private var packedPayloadOrigin: Vec3? = null
    private var packedPayloadLegacyCount = Int.MIN_VALUE
    private var packedPayloadCommandCount = 0
    private var packedPayloadNeedsMetadata = false
    /** 仅含静态零速度粒子的 system 可跳过 GPU 位移 dispatch。 */
    private var gpuNoopSimulation = true
    private val warnedBindingMismatches = HashSet<Pair<CParticleTextureBindingKey, CParticleTextureBindingKey?>>()

    internal var lastDynamicPrepareFrame = Long.MIN_VALUE
        private set

    var released = false
        private set

    // ------------------------------------------------------------ spawn

    /**
     * 生成一个粒子 (客户端渲染线程).
     * @param uvOverride 指定固定 UV；为 null 时注册 sprite/effect 动画描述符
     * @return 槽位, -1 = 池满
     */
    fun spawn(p: CParticle, uvOverride: CParticleSprites.UvRect? = null): Int {
        if (uvOverride != null) {
            val uv = uvOverride.toUv()
            val descriptorId = CParticleTextureDescriptors.register(
                textureBindingKey to uv,
                textureBindingKey,
            ) {
                listOf(uv)
            }
            val base = CParticleResolvedTexture(
                    textureBindingKey,
                    descriptorId,
                    uv,
                    null,
                    Vector3f(1F),
            )
            val maskSource = p.textureSource
            val mask = maskSource?.let { CParticleTextureResolver.resolve(it, p.pos) }
            return spawnResolved(
                p,
                CParticleResolvedTextures(
                    base,
                    mask,
                    randomBaseQuarterUv = false,
                    randomMaskQuarterUv = (maskSource as? CParticleTextureSource.Block)?.randomCrop == true,
                ),
            )
        }
        return spawnResolved(p, p.resolveTextures(p.pos))
    }

    /** 使用已注册的动画描述符生成；供外部纹理适配层使用。 */
    fun spawn(p: CParticle, animationId: Int): Int {
        val validatedId = CParticleTextureDescriptors.requireBinding(animationId, textureBindingKey)
        val base = CParticleResolvedTexture(
                textureBindingKey,
                validatedId,
                CParticleTextureDescriptors.firstFrame(validatedId),
                validatedId,
                Vector3f(1F),
        )
        val maskSource = p.textureSource
        val mask = maskSource?.let { CParticleTextureResolver.resolve(it, p.pos) }
        return spawnResolved(
            p,
            CParticleResolvedTextures(
                base,
                mask,
                randomBaseQuarterUv = false,
                randomMaskQuarterUv = (maskSource as? CParticleTextureSource.Block)?.randomCrop == true,
            ),
        )
    }

    /**
     * 使用已解析纹理生成粒子，供 manager 避免二次模型解析。
     *
     * 示例：默认批次先按基础和蒙版 binding 选 system，再调用本方法。
     * 禁止：[resolved] 的任一 binding 都必须与当前 system 相同。
     *
     * @param p 粒子生成描述
     * @param resolved 客户端统一双纹理解析结果
     * @param storagePosition 可选槽位写入坐标；为 `null` 时从粒子世界坐标逆变换
     * @return 槽位；达到全局上限、其他失败或 binding 不匹配时返回 `-1`
     */
    internal fun spawnResolved(
        p: CParticle,
        resolved: CParticleResolvedTextures,
        storagePosition: Vec3? = null,
    ): Int {
        if (released || !resolved.isValid) return -1
        if (resolved.base.bindingKey != textureBindingKey ||
            resolved.mask?.bindingKey != maskTextureBindingKey
        ) {
            warnBindingMismatch(resolved.base.bindingKey, resolved.mask?.bindingKey)
            return -1
        }
        val worldPosition = p.pos
        if (store.aliveCount == 0) snapGroupTransform()
        if (storagePosition == null) {
            rebaseIfNeeded(worldPosition)
        }
        val resolvedStoragePosition = storagePosition ?: resolveStoragePosition(worldPosition) ?: return -1
        if (gpuNoopSimulation &&
            (p.updateMode != CParticleUpdateMode.STATIC ||
                p.velocity.lengthSqr() > 1e-12 ||
                p.angularVelocity.lengthSquared() > 1e-12 ||
                p.blockCollision)
        ) {
            gpuNoopSimulation = false
        }
        val randomSeed = p.randomSeed ?: CParticleGpuMath.nextAutomaticSeed()
        var block = p.light
        var sky = p.light
        if (p.light < 0) {
            // 采样世界光照 (生成时一次)
            val world = Minecraft.getInstance().level
            if (world != null) {
                val packedLight = LevelRenderer.getLightColor(world, BlockPos.containing(worldPosition))
                block = (packedLight shr 4) and 15
                sky = (packedLight shr 20) and 15
            } else {
                block = 15; sky = 15
            }
        }
        return store.spawnWithMask(
            p,
            origin,
            resolved.base.animationId ?: resolved.base.descriptorId,
            block,
            sky,
            epochTick = tickCount + 1,
            randomSeed = randomSeed,
            colorMultiplier = resolved.base.colorMultiplier,
            randomQuarterUv = resolved.randomBaseQuarterUv,
            textureBindingKey = textureBindingKey,
            textureGeneration = CParticleTextureResolver.generation,
            maskAnimationId = resolved.mask?.let { it.animationId ?: it.descriptorId },
            randomMaskQuarterUv = resolved.randomMaskQuarterUv,
            maskTextureBindingKey = maskTextureBindingKey,
            maskColorMultiplier = resolved.mask?.colorMultiplier,
            spawnPosition = resolvedStoragePosition,
            sourceId = sourceId,
            sign = p.sign,
            commandMask = p.commandMask,
            metadataFlags = p.metadataFlags,
            charge = p.charge,
            mass = p.mass,
            radius = p.radius,
            // resolver 已验证基础/蒙版 descriptor，appearance 也由本 system 的 CParticle 描述注册。
            // 公开低层 Store 入口仍默认保留逐次校验；emitter 热路径跳过重复检查。
            validateDescriptors = false,
        )
    }

    private fun warnBindingMismatch(
        actual: CParticleTextureBindingKey,
        actualMask: CParticleTextureBindingKey?,
    ) {
        if (!warnedBindingMismatches.add(actual to actualMask)) return
        CooParticlesConstants.logger.warn(
            "CParticle system '{}' uses base {} and mask {}, rejected base {} and mask {}",
            name,
            textureBindingKey,
            maskTextureBindingKey,
            actual,
            actualMask,
        )
    }

    private fun rebaseIfNeeded(pos: Vec3) {
        if (store.aliveCount == 0 && pos.distanceToSqr(origin) > 1024.0 * 1024.0) {
            origin = pos
        }
    }

    /** 手动设置原点 (仅在池为空时生效) */
    fun setOriginIfEmpty(pos: Vec3) {
        if (store.aliveCount == 0) origin = pos
    }

    /**
     * 从当前 system tick 开始播放一次 GPU 视觉过渡。
     *
     * alpha 和 scale 曲线作为粒子原始值的倍率；同时提供 [colorFrom]、[colorTo]
     * 时，颜色在两者之间插值。相同配置的重复调用不会重置进度；需要重播时传入 [restart]。
     * 该操作不会改写粒子实例缓冲。
     * 示例：`playVisualTransition(20F, scaleCurve = CParticleCurve.linear(1F, 0F))`。
     * 禁止：不要传入非正数或非有限的 [durationTicks]。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色，必须与 [colorTo] 同时设置
     * @param colorTo 可选结束颜色，必须与 [colorFrom] 同时设置
     * @param mode 过渡结束后的行为
     * @return 当前系统
     * @throws IllegalArgumentException 参数组合无效时抛出
     */
    @JvmOverloads
    fun playVisualTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve? = null,
        scaleCurve: CParticleCurve? = null,
        colorFrom: Vector3fc? = null,
        colorTo: Vector3fc? = null,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
    ): CParticleSystem {
        return playVisualTransitionInternal(
            durationTicks,
            alphaCurve,
            scaleCurve,
            colorFrom,
            colorTo,
            mode,
            restart = false,
        )
    }

    /**
     * 播放 GPU 视觉过渡，并允许强制重新开始相同配置。
     *
     * 示例：`playVisualTransition(20F, true, scaleCurve = curve)` 会重置进度。
     * 禁止：不要传入非正数或非有限的 [durationTicks]。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param restart 是否强制从头播放
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色，必须与 [colorTo] 同时设置
     * @param colorTo 可选结束颜色，必须与 [colorFrom] 同时设置
     * @param mode 过渡结束后的行为
     * @return 当前系统
     * @throws IllegalArgumentException 参数组合无效时抛出
     */
    @JvmOverloads
    fun playVisualTransition(
        durationTicks: Float,
        restart: Boolean,
        alphaCurve: CParticleCurve? = null,
        scaleCurve: CParticleCurve? = null,
        colorFrom: Vector3fc? = null,
        colorTo: Vector3fc? = null,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
    ): CParticleSystem {
        return playVisualTransitionInternal(
            durationTicks,
            alphaCurve,
            scaleCurve,
            colorFrom,
            colorTo,
            mode,
            restart,
        )
    }

    /**
     * 校验并保存一段系统级视觉过渡。
     *
     * 示例：两个公开重载都通过本方法统一处理 [restart]。
     * 禁止：不要绕过这里的时长和颜色参数校验。
     *
     * @param durationTicks 过渡时长，单位 tick
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve 等比缩放倍率曲线
     * @param colorFrom 可选起始颜色
     * @param colorTo 可选结束颜色
     * @param mode 过渡结束后的行为
     * @param restart 是否强制替换相同配置
     * @return 当前系统
     * @throws IllegalArgumentException 参数组合无效时抛出
     */
    private fun playVisualTransitionInternal(
        durationTicks: Float,
        alphaCurve: CParticleCurve?,
        scaleCurve: CParticleCurve?,
        colorFrom: Vector3fc?,
        colorTo: Vector3fc?,
        mode: CParticleTransitionMode,
        restart: Boolean,
    ): CParticleSystem {
        require(durationTicks.isFinite() && durationTicks > 0F) {
            "durationTicks must be finite and greater than zero"
        }
        require((colorFrom == null) == (colorTo == null)) {
            "colorFrom and colorTo must both be set or both be null"
        }
        require(alphaCurve != null || scaleCurve != null || colorFrom != null) {
            "visual transition requires an alpha curve, scale curve, or color range"
        }
        if (!restart && visualTransition?.matches(
                durationTicks,
                alphaCurve,
                scaleCurve,
                colorFrom,
                colorTo,
                mode,
            ) == true
        ) {
            return this
        }
        visualTransition = CParticleVisualTransition(
            startTick = tickCount.toFloat(),
            durationTicks = durationTicks,
            alphaCurve = alphaCurve,
            scaleCurve = scaleCurve,
            colorFrom = colorFrom,
            colorTo = colorTo,
            mode = mode,
        )
        return this
    }

    /**
     * 播放独立的 system alpha 过渡。过渡生效时，曲线值会覆盖粒子实例的 alpha，
     * 再与粒子生命周期、system alpha 和视觉过渡的 alpha 曲线相乘。
     * 此方法不会替换颜色或大小视觉过渡。
     *
     * @param durationTicks 过渡时长，单位为 tick，必须为有限正数
     * @param alphaCurve 用于覆盖粒子实例 alpha 的曲线
     * @param mode 过渡结束后的行为
     * @param restart 是否强制替换相同配置
     * @return 当前系统
     * @throws IllegalArgumentException 当 [durationTicks] 不是有限正数时抛出
     */
    @JvmOverloads
    fun playAlphaTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
        restart: Boolean = false,
    ): CParticleSystem {
        require(durationTicks.isFinite() && durationTicks > 0F) {
            "durationTicks must be finite and greater than zero"
        }
        if (!restart && alphaTransition?.matches(
                durationTicks,
                alphaCurve,
                null,
                null,
                null,
                mode,
            ) == true
        ) {
            return this
        }
        // tickCount 会在本轮 system tick 末尾自增；下一次渲染应从曲线起点开始。
        alphaTransition = CParticleVisualTransition(
            startTick = tickCount.toFloat() + 1F,
            durationTicks = durationTicks,
            alphaCurve = alphaCurve,
            scaleCurve = null,
            colorFrom = null,
            colorTo = null,
            mode = mode,
        )
        return this
    }

    /**
     * 停止当前过渡。[reset] 为 true 时恢复原始状态，否则立即停在最终状态。
     */
    @JvmOverloads
    fun stopVisualTransition(reset: Boolean = false): CParticleSystem {
        val current = visualTransition ?: return this
        visualTransition = if (reset) {
            null
        } else {
            CParticleVisualTransition(
                startTick = tickCount.toFloat() - current.durationTicks,
                durationTicks = current.durationTicks,
                alphaCurve = current.alphaCurve,
                scaleCurve = current.scaleCurve,
                colorFrom = current.colorFrom.takeIf { current.hasColor },
                colorTo = current.colorTo.takeIf { current.hasColor },
                mode = CParticleTransitionMode.HOLD_END,
            )
        }
        return this
    }

    /** 停止独立 alpha 过渡。[reset] 为 false 时停在曲线终点。 */
    @JvmOverloads
    fun stopAlphaTransition(reset: Boolean = false): CParticleSystem {
        val current = alphaTransition ?: return this
        alphaTransition = if (reset) {
            null
        } else {
            CParticleVisualTransition(
                startTick = tickCount.toFloat() - current.durationTicks,
                durationTicks = current.durationTicks,
                alphaCurve = current.alphaCurve,
                scaleCurve = null,
                colorFrom = null,
                colorTo = null,
                mode = CParticleTransitionMode.HOLD_END,
            )
        }
        return this
    }

    // ------------------------------------------------------------ scripted 句柄写入

    fun checkHandle(slot: Int, generation: Int): Boolean =
        !released && store.isAlive(slot) && store.generations[slot] == generation

    /** scripted: 写位置 (世界坐标); 同 tick 首次写入自动滚动 prev 实现插值 */
    fun scriptedSetPos(slot: Int, generation: Int, pos: Vec3) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        val relativeX = (pos.x - origin.x).toFloat()
        val relativeY = (pos.y - origin.y).toFloat()
        val relativeZ = (pos.z - origin.z).toFloat()
        val transformed = if (hasIdentityGroupTransform()) {
            null
        } else {
            val inverse = inverseGroupTransform() ?: return
            inverse.transformPosition(Vector3f(relativeX, relativeY, relativeZ))
        }
        val base = slot * CParticleStore.STRIDE
        val d = store.data
        if (store.writeTicks[slot] != tickCount) {
            d[base + CParticleStore.OFF_PREV] = d[base]
            d[base + CParticleStore.OFF_PREV + 1] = d[base + 1]
            d[base + CParticleStore.OFF_PREV + 2] = d[base + 2]
            store.writeTicks[slot] = tickCount
        }
        d[base] = transformed?.x ?: relativeX
        d[base + 1] = transformed?.y ?: relativeY
        d[base + 2] = transformed?.z ?: relativeZ
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedSetColor(slot: Int, generation: Int, r: Float, g: Float, b: Float) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR
        store.data[base] = r; store.data[base + 1] = g; store.data[base + 2] = b
        store.markDirty(slot)
    }

    fun scriptedSetAlpha(slot: Int, generation: Int, alpha: Float) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR + 3] = alpha.coerceIn(0F, 1F)
        store.markDirty(slot)
    }

    fun scriptedSetSize(slot: Int, generation: Int, w: Float, h: Float) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_SIZE
        store.data[base] = w; store.data[base + 1] = h
        store.markDirty(slot)
    }

    fun scriptedSetAge(slot: Int, generation: Int, age: Int) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.setAge(slot, age, lifecycleEpochTick(slot))
    }

    fun scriptedSetRotation(slot: Int, generation: Int, yaw: Float, pitch: Float, roll: Float) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.setBaseRotation(slot, pitch, yaw, roll, lifecycleEpochTick(slot))
    }

    fun scriptedSetRotationDirection(slot: Int, generation: Int, direction: Vector3f?) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.setRotationDirection(slot, direction, lifecycleEpochTick(slot))
    }

    fun scriptedSetAngularVelocity(slot: Int, generation: Int, velocity: Vector3f) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.setAngularVelocity(slot, velocity, lifecycleEpochTick(slot))
    }

    fun scriptedAddRoll(slot: Int, generation: Int, radians: Float) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        store.addRoll(slot, radians, lifecycleEpochTick(slot))
    }

    private fun lifecycleEpochTick(slot: Int): Int =
        if (store.isPendingSpawn(slot)) tickCount + 1 else tickCount

    fun scriptedGetRotation(slot: Int, generation: Int): Vector3f? {
        if (!checkHandle(slot, generation)) return null
        return store.currentRotation(slot, tickCount)
    }

    fun scriptedGetRotationDirection(slot: Int, generation: Int): Vector3f? {
        if (!checkHandle(slot, generation)) return null
        return store.rotationDirection(slot)
    }

    fun scriptedGetAngularVelocity(slot: Int, generation: Int): Vector3f? {
        if (!checkHandle(slot, generation)) return null
        return store.angularVelocity(slot)
    }

    fun scriptedSetVelocity(slot: Int, generation: Int, velocity: Vec3) {
        if (!checkHandle(slot, generation)) return
        if (mode == CParticleSystemMode.SIMULATED) disableGpuNoopSimulation()
        val localVelocity = if (hasIdentityGroupTransform()) {
            null
        } else {
            val inverse = inverseGroupTransform() ?: return
            inverse.transformDirection(
                Vector3f(velocity.x.toFloat(), velocity.y.toFloat(), velocity.z.toFloat())
            )
        }
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_VEL
        store.data[base] = localVelocity?.x ?: velocity.x.toFloat()
        store.data[base + 1] = localVelocity?.y ?: velocity.y.toFloat()
        store.data[base + 2] = localVelocity?.z ?: velocity.z.toFloat()
        store.markDirty(slot)
        settleTicks = 2
    }

    fun scriptedGetPos(slot: Int, generation: Int): Vec3? {
        if (!checkHandle(slot, generation)) return null
        val base = slot * CParticleStore.STRIDE
        if (hasIdentityGroupTransform()) {
            return Vec3(
                store.data[base] + origin.x,
                store.data[base + 1] + origin.y,
                store.data[base + 2] + origin.z,
            )
        }
        val transformed = groupTransform.transformPosition(
            Vector3f(store.data[base], store.data[base + 1], store.data[base + 2])
        )
        return Vec3(
            transformed.x + origin.x,
            transformed.y + origin.y,
            transformed.z + origin.z,
        )
    }

    fun scriptedGetVelocity(slot: Int, generation: Int): Vec3? {
        if (!checkHandle(slot, generation)) return null
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_VEL
        if (hasIdentityGroupTransform()) {
            return Vec3(
                store.data[base].toDouble(),
                store.data[base + 1].toDouble(),
                store.data[base + 2].toDouble(),
            )
        }
        val transformed = groupTransform.transformDirection(
            Vector3f(store.data[base], store.data[base + 1], store.data[base + 2])
        )
        return Vec3(
            transformed.x.toDouble(),
            transformed.y.toDouble(),
            transformed.z.toDouble(),
        )
    }

    fun scriptedGetAge(slot: Int, generation: Int): Int? {
        if (!checkHandle(slot, generation)) return null
        return store.getAge(slot)
    }

    /**
     * 读取 scripted 槽位的状态副本。句柄失效或系统不是 scripted 模式时返回 null。
     */
    internal fun snapshot(slot: Int, generation: Int): CParticle? {
        if (mode != CParticleSystemMode.SCRIPTED || !checkHandle(slot, generation)) return null
        val pos = scriptedGetPos(slot, generation) ?: return null
        val velocity = scriptedGetVelocity(slot, generation) ?: return null
        val base = slot * CParticleStore.STRIDE
        val data = store.data
        val flags = data[base + CParticleStore.OFF_FLAGS].toInt()
        val cameraMode = (flags ushr CParticleStore.CAMERA_SHIFT) and 3
        val blockLight = (flags ushr CParticleStore.BLOCK_LIGHT_SHIFT) and 15
        val skyLight = (flags ushr CParticleStore.SKY_LIGHT_SHIFT) and 15

        return CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            this.pos = pos
            this.velocity = velocity
            uniformSize = false
            weightSize = data[base + CParticleStore.OFF_SIZE]
            heightSize = data[base + CParticleStore.OFF_SIZE + 1]
            uniformSize = weightSize == heightSize
            color = Vector3f(
                data[base + CParticleStore.OFF_COLOR],
                data[base + CParticleStore.OFF_COLOR + 1],
                data[base + CParticleStore.OFF_COLOR + 2],
            )
            alpha = data[base + CParticleStore.OFF_COLOR + 3]
            age = store.ages[slot]
            maxAge = store.maxAges[slot]
            light = if (blockLight == skyLight) blockLight else -1
            cameraOption = ParticleCameraOption.entries.getOrElse(cameraMode) {
                ParticleCameraOption.BILLBOARD
            }
            val direction = store.rotationDirection(slot)
            if (direction == null) {
                axis = Vec3(
                    data[base + CParticleStore.OFF_AXIS].toDouble(),
                    data[base + CParticleStore.OFF_AXIS + 1].toDouble(),
                    data[base + CParticleStore.OFF_AXIS + 2].toDouble(),
                )
            }
            angularVelocity = store.angularVelocity(slot)
            val rotation = store.currentRotation(slot, tickCount)
            pitch = rotation.x
            yaw = rotation.y
            roll = rotation.z
            randomAgePreTick = flags and CParticleStore.FLAG_RANDOM_AGE != 0
            randomSeed = CParticleGpuMath.joinSeed(
                data[base + CParticleStore.OFF_ANIMATION + 2].toInt(),
                data[base + CParticleStore.OFF_ANIMATION + 3].toInt(),
            )
            CParticleAppearanceDescriptors.applyTo(
                this,
                data[base + CParticleStore.OFF_APPEARANCE].toInt(),
            )
        }
    }

    private fun inverseGroupTransform(): Matrix4f? {
        return inverseAffine(groupTransform)
    }

    /**
     * 把 SCRIPTED 粒子的渲染世界坐标换算为槽位写入坐标。
     *
     * 示例：整组平移 10 格后，在世界坐标 11 生成的粒子会写入局部坐标 1。
     * 禁止对 SIMULATED system 应用该换算；其生成位置由模拟器直接接管。
     *
     * @param worldPosition 粒子应当显示和采样环境的世界坐标
     * @return 供 [CParticleStore] 写入的世界坐标形式；矩阵不可逆时返回 `null`
     */
    private fun resolveStoragePosition(worldPosition: Vec3): Vec3? {
        if (mode != CParticleSystemMode.SCRIPTED || hasIdentityGroupTransform()) {
            return worldPosition
        }
        val inverse = inverseGroupTransform() ?: return null
        val transformed = inverse.transformPosition(
            Vector3f(
                (worldPosition.x - origin.x).toFloat(),
                (worldPosition.y - origin.y).toFloat(),
                (worldPosition.z - origin.z).toFloat(),
            )
        )
        return origin.add(
            transformed.x.toDouble(),
            transformed.y.toDouble(),
            transformed.z.toDouble(),
        )
    }

    private fun inverseAffine(matrix: Matrix4fc): Matrix4f? {
        val determinant = matrix.determinant3x3()
        if (!determinant.isFinite() || determinant == 0F) return null
        return Matrix4f(matrix).invertAffine()
    }

    private fun hasIdentityGroupTransform(): Boolean {
        return groupTransform.properties() and Matrix4fc.PROPERTY_IDENTITY.toInt() != 0
    }

    fun kill(slot: Int, generation: Int) {
        if (!checkHandle(slot, generation)) return
        store.kill(slot)
        settleTicks = 2
    }

    // ------------------------------------------------------------ tick

    /**
     * 每客户端 tick 调用 (渲染线程, 持有 GL 上下文).
     *
     * tickCount 在**末尾**自增: scripted 写入发生在本方法之前 (composition tick 早于 manager tick),
     * 写入盖章用的是当前值, [rollPrevForUnwritten] 必须用同一个值比较 —
     * 否则本 tick 刚写入的槽位会被误滚动 prev, 破坏插值.
     */
    fun tick(deferGpuMemoryBarrier: Boolean = false) {
        if (released) return
        prepareGroupTransformForTick()
        try {
            clearFinishedResetTransition()
            if (store.aliveCount == 0 && store.spawnedCount == 0 && store.killedCount == 0) {
                store.clearDirty()
                store.deathTracker?.drain()
                return
            }
            ensureGl()
            when (mode) {
                CParticleSystemMode.SIMULATED -> tickSimulated(deferGpuMemoryBarrier)
                CParticleSystemMode.SCRIPTED -> tickScripted()
            }
        } finally {
            tickCount++
        }
        // 死亡回调可能映射 GPU VBO 读取最终位置；延迟 barrier 时在回调前发布。
        if (deferGpuMemoryBarrier &&
            mode == CParticleSystemMode.SIMULATED &&
            gpuSimulationSelected &&
            store.deathTracker != null
        ) {
            CParticleGpuSimulator.publishMemoryBarrier()
        }
        store.deathTracker?.drain()
    }

    /**
     * 提交本 tick 的整组矩阵，所有槽位共用同一段 previous/current 插值。
     *
     * 示例：序列新增槽位后，已有槽位和新槽位一起从上一矩阵插值到当前矩阵。
     * 禁止单独改写新槽位的 previous 端点，否则生成期间会与主体旋转脱节。
     */
    private fun prepareGroupTransformForTick() {
        previousGroupTransform.set(currentGroupTransform)
        currentGroupTransform.set(groupTransform)
    }

    private fun clearFinishedResetTransition() {
        visualTransition = clearFinishedResetTransition(visualTransition)
        alphaTransition = clearFinishedResetTransition(alphaTransition)
    }

    private fun clearFinishedResetTransition(
        transition: CParticleVisualTransition?,
    ): CParticleVisualTransition? {
        if (transition == null || transition.mode != CParticleTransitionMode.RESET) return transition
        return transition.takeIf { tickCount - it.startTick < it.durationTicks }
    }

    private fun tickSimulated(deferGpuMemoryBarrier: Boolean) {
        // no-op 只能覆盖从未执行过真实模拟的静态池。即使本 tick 的 Force/变换后来被清空，
        // 旧粒子的 GPU 位置和速度也可能已经改变，不能把它们误当成静态粒子。
        if (forces.isNotEmpty() || forceSink.size > 0 ||
            transformsSimulatedParticleSpace || store.blockCollisionCount > 0 ||
            CParticleRespawnEngine.owns(this)
        ) {
            disableGpuNoopSimulation()
        }
        // 纯静态零运动池无需打包 Force、查询碰撞网格或进入 compute batch；
        // 顶点 shader 通过 epochTick 推导视觉年龄，CPU ledger 负责到期回收。
        if (!CParticleCapabilities.forceCpuSimulation && canSkipGpuDispatch() && store.killedCount == 0) {
            tickNoopSimulation()
            return
        }
        // 旧 1..9 且 selector=All 的批次保留 uniform 快路径；其数学仍等价于 Command。
        val payloadCached = forcesSyncTick != Int.MIN_VALUE &&
            packedPayloadRevision == forcesSyncTick &&
            packedPayloadPathRevision == CParticlePathLibrary.layerRevision &&
            packedPayloadOrigin == origin
        val legacyForceCount: Int
        val commandCount: Int
        if (payloadCached) {
            legacyForceCount = packedPayloadLegacyCount
            commandCount = packedPayloadCommandCount
            packedCommandsNeedMetadata = if (legacyForceCount >= 0) false else packedPayloadNeedsMetadata
        } else {
            legacyForceCount = packLegacyForces()
            commandCount = if (legacyForceCount >= 0) {
                packedCommandsNeedMetadata = false
                0
            } else {
                CParticlePerfProbe.measure(CParticlePerfProbe.Stage.COMMAND_PACK) { packCommands() }
            }
            if (forcesSyncTick != Int.MIN_VALUE) {
                packedPayloadRevision = forcesSyncTick
                packedPayloadPathRevision = CParticlePathLibrary.layerRevision
                packedPayloadOrigin = origin
                packedPayloadLegacyCount = legacyForceCount
                packedPayloadCommandCount = commandCount
                packedPayloadNeedsMetadata = packedCommandsNeedMetadata
            }
        }
        val simulationTransform = currentGroupTransform.takeIf {
            transformsSimulatedParticleSpace && !isIdentityTransform(it)
        }
        val inverseSimulationTransform = simulationTransform?.let(::inverseAffine)
        val collisionGrid = if (store.blockCollisionCount > 0) {
            CParticleBlockCollisionGridManager.gridFor(simulationCenter(), blockCollisionRange)
        } else {
            null
        }
        val prepared = CParticleRespawnEngine.owns(this)
        // GPU 是 CParticle 的默认模拟后端；只有显式配置小池 CPU 阈值时才允许回退。
        val useGpu = shouldUseGpuSimulation(prepared)
        check(useGpu || !prepared) { "Clear prepared GPU lifetimes before switching simulation backend" }
        if (!prepared && store.spawnedCount > 0 && (!useGpu || legacyForceCount >= 0 || !packedCommandsNeedMetadata)) {
            // metadata 未在本 tick 上传；以后重新启用 selector/Charge 时必须整段重传。
            metadataGpuSynchronized = false
        }
        if (useGpu) {
            val noopSimulation = gpuNoopSimulation &&
                !prepared && simulationTransform == null && collisionGrid == null &&
                legacyForceCount == 0 && commandCount == 0
            if (noopSimulation && store.killedCount == 0) {
                tickNoopSimulation()
                return
            }
            // compute 必须先看到本 tick 的死亡和新生成槽位，否则 CPU age 会领先 GPU 一 tick。
            if (store.killedCount > 0) {
                glBuffer.patchFlags(store.data, store.killedSlots, store.killedCount)
            }
            if (store.spawnedCount > 0) {
                glBuffer.uploadSlots(store.data, store.spawnedSlots, store.spawnedCount)
            }
            if (prepared) {
                ensureRespawnMetadata()
                if (store.spawnedCount > 0) metadataGlBuffer.uploadSlots(store.metadata, store.spawnedSlots, store.spawnedCount)
            }
            if (legacyForceCount < 0 && commandCount > 0) {
                ensureCommandGl(packedCommandsNeedMetadata)
                var fullMetadataUpload = false
                if (packedCommandsNeedMetadata && !metadataGpuSynchronized && store.aliveCount > 0) {
                    CParticlePerfProbe.measure(CParticlePerfProbe.Stage.METADATA_FIRST_UPLOAD) {
                        metadataGlBuffer.uploadRange(store.metadata, store.firstAliveSlot, store.highWater - 1)
                    }
                    metadataGpuSynchronized = true
                    fullMetadataUpload = true
                }
                if (!prepared && packedCommandsNeedMetadata && store.spawnedCount > 0 && !fullMetadataUpload) {
                    metadataGlBuffer.uploadSlots(store.metadata, store.spawnedSlots, store.spawnedCount)
                }
                commandGlBuffer.upload(packedCommands, commandCount)
                // 路径结束通道必须在 dispatch 前清零，否则 CPU 会重复回收上一 tick 的槽位。
                pathEndBuffer.resetCounter()
            }
            CParticleGpuSimulator.simulate(
                this,
                packedForces,
                legacyForceCount,
                packedCommands,
                commandCount,
                packedCommandsNeedMetadata,
                collisionGrid,
                simulationTransform,
                inverseSimulationTransform,
                deferMemoryBarrier = deferGpuMemoryBarrier,
            )
            // 路径结束列表是 GPU→CPU 的读回通道；延迟 barrier 时必须在读取前发布。
            if (deferGpuMemoryBarrier &&
                legacyForceCount < 0 &&
                CParticleRespawnEngine.requiresPathReadback(this)
            ) {
                CParticleGpuSimulator.publishMemoryBarrier()
            }
            // compute 已经写过内存屏障，此时读回结束槽位与 CPU 账本一致。
            collectingGpuDeaths = true
            try {
                if (legacyForceCount < 0 && CParticleRespawnEngine.requiresPathReadback(this)) drainPathEndedSlots()
                store.tickGpuAges(tickCount)
            } finally {
                collectingGpuDeaths = false
                closeDeathReadback()
            }
            store.publishDynamicAges()
            // compute 已经在 GPU SSBO 中清除了 NEWBORN；CPU 侧只需批量清空 pending 位图。
            store.clearSpawnedAfterGpu()
            store.clearKilled()
            store.clearDirty()
        } else {
            // 只有调用方显式设置 forceCpuSimulation 才走 CPU；GPU 执行错误不会进入这里。
            if (legacyForceCount >= 0) {
                CParticleCpuSimulator.simulate(
                    store, packedForces, legacyForceCount,
                    origin.x, origin.y, origin.z, speedLimit, collisionGrid,
                    simulationTransform, inverseSimulationTransform,
                )
            } else {
                CParticleCpuSimulator.simulate(
                    store, packedCommands, commandCount, forceResourceTable,
                    origin.x, origin.y, origin.z, speedLimit, collisionGrid,
                    simulationTransform, inverseSimulationTransform,
                )
            }
            store.tickAges(writeBufferAge = true)
            store.publishDynamicAges()
            store.clearSpawned()
            uploadDirty()
            store.clearKilled()
        }
    }

    /** 处理 no-op 静态池的出生上传与 CPU 生命周期，不触碰 compute/Force/碰撞路径。 */
    private fun tickNoopSimulation() {
        if (store.spawnedCount > 0) {
            store.clearSpawnedFlagsForNoop()
            glBuffer.uploadSlots(store.data, store.spawnedSlots, store.spawnedCount)
        }
        store.tickGpuAges(tickCount, queueKilledFlags = true)
        if (store.killedCount > 0) {
            glBuffer.patchFlags(store.data, store.killedSlots, store.killedCount)
            store.clearKilled()
        }
        store.clearSpawnedAfterGpu()
        store.clearDirty()
    }

    private fun tickScripted() {
        // 未被写入的槽位滚动 prev=cur (静止粒子收敛, 避免重复插值)
        if (settleTicks > 0) {
            rollPrevForUnwritten()
            settleTicks--
            store.markAllAliveDirty()
        }
        store.tickAges(writeBufferAge = false)
        store.publishDynamicAges()
        store.clearSpawned()
        uploadDirty()
        store.clearKilled()
    }

    private fun rollPrevForUnwritten() {
        val d = store.data
        val bits = store.aliveBits
        val words = (store.highWater + 63) ushr 6
        for (w in 0 until words) {
            var b = bits[w]
            while (b != 0L) {
                val bit = b.countTrailingZeroBits()
                b = b and (b - 1)
                val slot = (w shl 6) + bit
                if (store.writeTicks[slot] != tickCount) {
                    val base = slot * CParticleStore.STRIDE
                    d[base + CParticleStore.OFF_PREV] = d[base]
                    d[base + CParticleStore.OFF_PREV + 1] = d[base + 1]
                    d[base + CParticleStore.OFF_PREV + 2] = d[base + 2]
                }
            }
        }
    }

    private fun uploadDirty() {
        if (store.dirtyMin >= 0) {
            glBuffer.uploadRange(store.data, store.dirtyMin, store.dirtyMax)
        }
        store.clearDirty()
    }

    /**
     * 死亡批次处理后只上传新生槽位，使其当前帧可见，不额外推进模拟或年龄。
     * 保留 spawned 标记，下一 tick 的 compute 仍按既有 newborn 语义处理。
     */
    internal fun uploadPendingSpawns() {
        if (released || store.spawnedCount == 0) return
        ensureGl()
        glBuffer.uploadSlots(store.data, store.spawnedSlots, store.spawnedCount)
    }

    /**
     * 回收 GPU 侧因路径到达终点而提前结束的槽位。
     *
     * 通道只回读一个 4 字节计数器，没有结束事件时不读取列表；计数器溢出时做一次兜底扫描，
     * 保证槽位不会被长期占住。回收走与正常死亡完全相同的 [CParticleStore.kill] 路径，
     * 因此全局容量统计、世代与复用机制保持一致。
     */
    private fun drainPathEndedSlots() {
        if (!pathEndBuffer.initialized) return
        pathEndBuffer.drain { slot -> releasePathEndedSlot(slot) }
        if (!pathEndBuffer.overflowed) return
        val data = store.data
        for (slot in store.firstAliveSlot until store.highWater) {
            if (!store.isAlive(slot)) continue
            if (data[slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and
                CParticleInstanceFlags.PATH_ENDED == 0
            ) {
                continue
            }
            releasePathEndedSlot(slot)
        }
    }

    private fun releasePathEndedSlot(slot: Int) {
        if (slot !in 0 until store.capacity) return
        if (!store.isAlive(slot)) return
        if (store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and
            CParticleInstanceFlags.RESPAWN_OWNED != 0) return
        store.kill(slot, queueGpuFlag = false, reason = RemoveReason.LIFECYCLE)
    }

    /** 每个渲染帧只补写一次 DYNAMIC 粒子的渲染字段。 */
    internal fun prepareDynamicVisuals(frameId: Long) {
        if (released || lastDynamicPrepareFrame == frameId) return
        lastDynamicPrepareFrame = frameId
        val dirtyCount = store.prepareDynamicTextures(
            tickCount,
            CParticleTextureResolver.generation,
            textureBindingKey,
            maskTextureBindingKey,
            resolveTextures = { particle -> particle.resolveTextures(particle.pos) },
            onBindingMismatch = { _, actual, actualMask -> warnBindingMismatch(actual, actualMask) },
        )
        if (dirtyCount > 0) {
            glBuffer.patchDynamicVisuals(store.data, store.dynamicDirtySlots, dirtyCount)
        }
    }

    private fun packCommands(): Int {
        forceSink.setExternalOverflow(
            (forces.size + forceSink.size - ForceCommand.MAX_COMMANDS).coerceAtLeast(0),
        )
        packedCommands.fill(0F)
        packedCommandsNeedMetadata = false
        forceResourceTable.clear()
        currentPathLayerVersion = CParticlePathLibrary.layerRevision
        var count = 0
        for (force in forces) {
            if (count >= ForceCommand.MAX_COMMANDS) break
            if (packCommand(ForceCommand(force), count * ForceCommand.STRIDE)) {
                packedCommandsNeedMetadata = true
            }
            count++
        }
        forceSink.forEach { command ->
            if (count < ForceCommand.MAX_COMMANDS) {
                if (packCommand(command, count * ForceCommand.STRIDE)) {
                    packedCommandsNeedMetadata = true
                }
                count++
            }
        }
        return count
    }

    /**
     * 尝试把全部 Force 编码为旧 uniform ABI。
     *
     * 返回 `-1` 表示当前批次包含 selector、新 Force、资源 Force、路径约束或超过旧上限，
     * 必须使用 Command SSBO；非负返回值表示可以安全走旧 1..9 kernel。
     */
    private fun packLegacyForces(): Int {
        packedForces.fill(0F)
        var count = 0
        var compatible = true

        fun append(force: CParticleForce): Boolean {
            if (force.typeId !in CParticleForce.TYPE_GRAVITY..CParticleForce.TYPE_FLOW_FIELD ||
                count >= CParticleForce.MAX_FORCES
            ) return false
            force.pack(packedForces, count * CParticleForce.STRIDE, origin)
            count++
            return true
        }

        for (force in forces) {
            if (!append(force)) compatible = false
        }
        forceSink.forEach { command ->
            if (command.selector.mode != CParticleSelector.All.mode || !append(command.force)) {
                compatible = false
            }
        }
        return if (compatible) count else -1
    }

    /**
     * 打包一条 Command。
     *
     * 路径位置约束需要解析路径图层槽位与图层重建版本，因此单独走一个分支；
     * 其余力沿用原有 ABI。
     *
     * @return 本命令是否让 kernel 需要读取粒子 metadata
     */
    private fun packCommand(command: ForceCommand, base: Int): Boolean {
        var needsMetadata = command.selector.mode != CParticleSelector.All.mode ||
            command.force.typeId == CParticleForce.TYPE_CHARGE ||
            command.force.typeId == CParticleForce.TYPE_LENNARD_JONES ||
            command.force.typeId == CParticleForce.TYPE_PATH_CONSTRAINT
        when (val force = command.force) {
            is CParticleForce.Texture -> command.pack(
                packedCommands,
                base,
                origin,
                forceResourceTable.slotFor(force.resource),
            )

            is CParticleForce.FluidFlow -> command.pack(
                packedCommands,
                base,
                origin,
                forceResourceTable.slotFor(force.resource),
            )

            is CParticleForce.Path -> {
                CParticlePathCommandPacker.pack(packedCommands, base, command, currentPathLayerVersion)
            }

            else -> command.pack(packedCommands, base, origin)
        }
        return needsMetadata
    }

    private fun simulationCenter(): Vec3 {
        if (!transformsSimulatedParticleSpace) return origin
        val transformed = currentGroupTransform.transformPosition(Vector3f())
        return origin + transformed
    }

    private fun isIdentityTransform(matrix: Matrix4fc): Boolean {
        return matrix.properties() and Matrix4fc.PROPERTY_IDENTITY.toInt() != 0
    }

    // ------------------------------------------------------------ 生命周期

    /** 首次使用时创建 GL 资源 (渲染线程) */
    fun ensureGl() {
        if (!glBuffer.initialized) {
            CParticlePerfProbe.measure(CParticlePerfProbe.Stage.GL_ALLOCATE) {
                glBuffer.init()
                metadataGpuSynchronized = false
                // 新缓冲: 把当前 CPU 侧数据整体上传 (含 shader 重载后重建的场景)
                if (store.aliveCount > 0) {
                    glBuffer.uploadRange(store.data, store.firstAliveSlot, store.highWater - 1)
                }
            }
        }
        if (!pathEndBuffer.initialized) {
            CParticlePerfProbe.measure(CParticlePerfProbe.Stage.GL_ALLOCATE) {
                pathEndBuffer.init()
            }
        }
    }

    private fun ensureCommandGl(metadataRequired: Boolean) {
        if (metadataRequired && !metadataGlBuffer.initialized) metadataGlBuffer.init()
        if (!commandGlBuffer.initialized) commandGlBuffer.init()
    }

    /** 预提交的出生 metadata 始终保留在 GPU，避免后续 selector 首次启用时覆盖它。 */
    internal fun ensureRespawnMetadata() {
        ensureGl()
        if (!metadataGlBuffer.initialized) metadataGlBuffer.init()
        if (!metadataGpuSynchronized) {
            if (store.aliveCount > 0) metadataGlBuffer.uploadRange(store.metadata, store.firstAliveSlot, store.highWater - 1)
            metadataGpuSynchronized = true
        }
    }

    /** 清空全部粒子 (保留 GL 资源) */
    fun clearParticles() {
        CParticleRespawnEngine.clearSystem(this)
        val prevHigh = store.highWater
        store.clear()
        metadataGpuSynchronized = false
        snapGroupTransform()
        settleTicks = 0
        lastDynamicPrepareFrame = Long.MIN_VALUE
        if (glBuffer.initialized && prevHigh > 0) {
            // 同步清掉 GPU 侧 alive 位
            glBuffer.uploadRange(store.data, 0, prevHigh - 1)
        }
    }

    /**
     * 将已空的普通 emitter system 恢复到新建状态，同时保留 CPU 数组和 GL 缓冲。
     * Manager 在放入空池缓存前调用；不得用于仍有粒子或后继生命的系统。
     * 清空槽位世代、死亡关联和命令引用，防止下一 emitter 继承上一实例的状态。
     */
    internal fun resetForEmitterReuse() {
        check(!released && store.aliveCount == 0 && mode == CParticleSystemMode.SIMULATED)
        clearParticles()
        forces.clear()
        forceSink.clear()
        forceResourceTable.clear()
        forcesSyncTick = Int.MIN_VALUE
        origin = Vec3.ZERO
        speedLimit = 32F
        blockCollisionRange = CParticleSystemManager.DEFAULT_BLOCK_COLLISION_RANGE
        alphaCurve = null
        scaleCurve = null
        colorCurve = null
        curveCycleTicks = 0F
        colorCycleTicks = 0F
        colorCycleSpatialScale = 0F
        visualTransition = null
        alphaTransition = null
        transformsSimulatedParticleSpace = false
        groupTransform.identity()
        snapGroupTransform()
        visibleRange = 256.0
        tickCount = 0
        gpuSimulationSelected = false
        gpuNoopSimulation = true
        warnedBindingMismatches.clear()
    }

    /** 仅释放 GL 资源 (shader/资源重载时; CPU 数据保留, 下次 ensureGl 重传) */
    fun releaseGl() {
        // GPU 预提交生命没有 CPU 运动镜像；显式释放缓冲时取消整条链，不能重播旧出生状态。
        if (CParticleRespawnEngine.owns(this)) clearParticles()
        glBuffer.release()
        metadataGlBuffer.release()
        commandGlBuffer.release()
        pathEndBuffer.release()
        metadataGpuSynchronized = false
    }

    /** 完全释放 (退出/调试) */
    fun release() {
        CParticleRespawnEngine.clearSystem(this)
        released = true
        store.clear()
        metadataGpuSynchronized = false
        lastDynamicPrepareFrame = Long.MIN_VALUE
        glBuffer.release()
        metadataGlBuffer.dispose()
        commandGlBuffer.dispose()
        pathEndBuffer.dispose()
    }

    /** 粗可见性：LOCAL emitter 使用变换后中心，放大时同步扩展可见半径。 */
    fun isVisible(cameraPos: Vec3): Boolean {
        if (store.aliveCount == 0) return false
        val r = visibleRange * simulationScale().coerceAtLeast(1.0) + 64.0
        return cameraPos.distanceToSqr(simulationCenter()) <= r * r
    }

    private fun simulationScale(): Double {
        if (!transformsSimulatedParticleSpace) return 1.0
        val matrix = currentGroupTransform
        val x = sqrt((matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02()).toDouble())
        val y = sqrt((matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11() + matrix.m12() * matrix.m12()).toDouble())
        val z = sqrt((matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21() + matrix.m22() * matrix.m22()).toDouble())
        return maxOf(x, y, z)
    }

    internal fun snapGroupTransform() {
        previousGroupTransform.set(groupTransform)
        currentGroupTransform.set(groupTransform)
    }
}

private val cParticleNextSourceId = AtomicInteger(1)

/**
 * 单个 system 每 tick 最多记录的路径提前结束槽位数量。
 *
 * 列表只记录结束槽位号，溢出时 GPU 继续增长计数器但不追加，CPU 会据此做一次兜底扫描，
 * 因此不需要按粒子容量分配。65536 个 int 为 256 KiB。
 */
private const val PATH_END_RECORD_CAPACITY = 65536

private fun nextSourceId(): Int {
    var id = cParticleNextSourceId.getAndIncrement()
    if (id == 0) id = cParticleNextSourceId.getAndIncrement()
    return id
}

enum class CParticleSystemMode {
    /** 发射器语义: 力场驱动, fire-and-forget */
    SIMULATED,

    /** composition 语义: CPU 位置权威 + 句柄控制 + 整组变换 */
    SCRIPTED
}
