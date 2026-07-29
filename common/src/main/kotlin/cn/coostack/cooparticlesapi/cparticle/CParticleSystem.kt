package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.collision.CParticleBlockCollisionGridManager
import cn.coostack.cooparticlesapi.cparticle.render.CParticleGlBuffer
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleGpuSimulator
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * # CParticleSystem — 一个 GPU 粒子池
 *
 * 一个系统 = 一份固定容量的 SoA 存储 + 一个 GL 实例缓冲 + 一个渲染层 + 一组力场.
 * 每帧一次 instanced draw; 每 tick 一次模拟 (GL43 compute 或 CPU 并行回退).
 *
 * 两种模式:
 * - [CParticleSystemMode.SIMULATED]: 发射器语义 — 粒子生成后由力场驱动, 不可单独控制
 *   (对应 "emitter 是数据源")
 * - [CParticleSystemMode.SCRIPTED]: composition 语义 — CPU 持有位置权威,
 *   通过句柄 teleport/rotate, 或整组 groupTransform 变换 (对应 "composition 是显示")
 */
class CParticleSystem(
    val name: String,
    val capacity: Int,
    val layer: CParticleRenderLayer,
    val mode: CParticleSystemMode,
    /**
     * 本系统所有实例共用的基础纹理绑定。
     *
     * Example: 同一方块图集系统可混合多种 BlockState。
     * Forbidden: 粒子生成后不能把槽位改到另一 binding。
     */
    val textureBindingKey: CParticleTextureBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
) {
    /** 构造阶段记录的蒙版 binding。Example: 方块蒙版使用 `BLOCK_ATLAS`。Forbidden: 初始化后不能改动。 */
    private var configuredMaskTextureBindingKey: CParticleTextureBindingKey? = null

    /**
     * 本 system 所有实例共用的可选蒙版纹理 binding。
     *
     * Example: 粒子图集基础纹理可以配 [CParticleTextureBindingKey.BLOCK_ATLAS] 蒙版。
     * Forbidden: 生成后不能把槽位切到另一蒙版图集。
     */
    val maskTextureBindingKey: CParticleTextureBindingKey?
        get() = configuredMaskTextureBindingKey

    /**
     * 创建同时固定基础纹理和蒙版纹理 binding 的粒子系统。
     *
     * Example: `CParticleSystem(name, capacity, layer, mode, PARTICLE_ATLAS, BLOCK_ATLAS)`。
     * Forbidden: 蒙版 binding 不能在系统存活期间改变。
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
     * Example: `store.spawn(...)` 仍会申请全局额度。
     * Forbidden: 不能用公开 store 入口绕过 [CParticleSystemManager.particleCountLimit]。
     */
    val store = CParticleStore.globallyCounted(capacity)
    val glBuffer = CParticleGlBuffer(capacity)

    /**
     * 系统原点 (双精度): 粒子位置以它为基准存 float,
     * 避免远坐标 float 精度抖动. 池为空时可自动重定位.
     */
    var origin: Vec3 = Vec3.ZERO
        private set

    /** 力场列表 (SIMULATED 模式生效) */
    val forces = ArrayList<CParticleForce>()

    /** emitter 桥接: 上次同步力场和视觉配置的 emitter tick (避免同 tick 重复重建) */
    var forcesSyncTick = Int.MIN_VALUE

    /** 速度上限 (对应 ControlableParticleData.speedLimit) */
    var speedLimit = 32f

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
     * Example: `scaleCurve = CParticleCurve.linear(1f, 0f)` 会让粒子逐渐缩小。
     * Forbidden: 不要用它设置粒子生成时的基础宽高。
     */
    var scaleCurve: CParticleCurve? = null

    /** 颜色生命周期倍率曲线 (null = 保持实例颜色) */
    var colorCurve: CParticleColorCurve? = null

    /** 大于 0 时，alpha/scale/color 曲线按系统 tick 循环，不再使用粒子生命周期进度。 */
    var curveCycleTicks = 0f

    /** 大于 0 时，颜色在指定 tick 周期内完成一次色相循环。 */
    var colorCycleTicks = 0f

    /** 颜色沿粒子绕系统原点的角度分布多少个循环。 */
    var colorCycleSpatialScale = 0f

    internal var visualTransition: CParticleVisualTransition? = null
        private set

    internal var alphaTransition: CParticleVisualTransition? = null
        private set

    /**
     * 整组变换 (SCRIPTED 模式): 施加于粒子的原点相对坐标.
     * 用它做整组旋转/缩放是零 per-particle CPU 开销的.
     */
    val groupTransform = Matrix4f()

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

    private val packedForces = FloatArray(CParticleGpuSimulator.PACKED_SIZE)
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
                    Vector3f(1f),
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
                Vector3f(1f),
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
        if (!CParticleSystemManager.hasAvailableParticleCapacity()) return -1
        val worldPosition = p.pos
        if (store.aliveCount == 0) snapGroupTransform()
        rebaseIfNeeded(worldPosition)
        val resolvedStoragePosition = storagePosition ?: resolveStoragePosition(worldPosition) ?: return -1
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
            epochTick = tickCount,
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
     * Example: `playVisualTransition(20f, scaleCurve = CParticleCurve.linear(1f, 0f))`。
     * Forbidden: 不要传入非正数或非有限的 [durationTicks]。
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
     * Example: `playVisualTransition(20f, true, scaleCurve = curve)` 会重置进度。
     * Forbidden: 不要传入非正数或非有限的 [durationTicks]。
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
     * Example: 两个公开重载都通过本方法统一处理 [restart]。
     * Forbidden: 不要绕过这里的时长和颜色参数校验。
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
        require(durationTicks.isFinite() && durationTicks > 0f) {
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
     * 播放独立的 system alpha 倍率过渡，不会替换颜色或大小视觉过渡。
     */
    @JvmOverloads
    fun playAlphaTransition(
        durationTicks: Float,
        alphaCurve: CParticleCurve,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
        restart: Boolean = false,
    ): CParticleSystem {
        require(durationTicks.isFinite() && durationTicks > 0f) {
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
        alphaTransition = CParticleVisualTransition(
            startTick = tickCount.toFloat(),
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
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR
        store.data[base] = r; store.data[base + 1] = g; store.data[base + 2] = b
        store.markDirty(slot)
    }

    fun scriptedSetAlpha(slot: Int, generation: Int, alpha: Float) {
        if (!checkHandle(slot, generation)) return
        store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR + 3] = alpha.coerceIn(0f, 1f)
        store.markDirty(slot)
    }

    fun scriptedSetSize(slot: Int, generation: Int, w: Float, h: Float) {
        if (!checkHandle(slot, generation)) return
        val base = slot * CParticleStore.STRIDE + CParticleStore.OFF_SIZE
        store.data[base] = w; store.data[base + 1] = h
        store.markDirty(slot)
    }

    fun scriptedSetAge(slot: Int, generation: Int, age: Int) {
        if (!checkHandle(slot, generation)) return
        store.setAge(slot, age, tickCount)
    }

    fun scriptedSetRotation(slot: Int, generation: Int, yaw: Float, pitch: Float, roll: Float) {
        if (!checkHandle(slot, generation)) return
        store.setBaseRotation(slot, pitch, yaw, roll, tickCount)
    }

    fun scriptedSetRotationDirection(slot: Int, generation: Int, direction: Vector3f?) {
        if (!checkHandle(slot, generation)) return
        store.setRotationDirection(slot, direction, tickCount)
    }

    fun scriptedSetAngularVelocity(slot: Int, generation: Int, velocity: Vector3f) {
        if (!checkHandle(slot, generation)) return
        store.setAngularVelocity(slot, velocity, tickCount)
    }

    fun scriptedAddRoll(slot: Int, generation: Int, radians: Float) {
        if (!checkHandle(slot, generation)) return
        store.addRoll(slot, radians, tickCount)
    }

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
        if (!determinant.isFinite() || determinant == 0f) return null
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
    fun tick() {
        if (released) return
        prepareGroupTransformForTick()
        try {
            clearFinishedResetTransition()
            if (store.aliveCount == 0 && store.spawnedCount == 0 && store.killedCount == 0) {
                store.clearDirty()
                return
            }
            ensureGl()
            when (mode) {
                CParticleSystemMode.SIMULATED -> tickSimulated()
                CParticleSystemMode.SCRIPTED -> tickScripted()
            }
        } finally {
            tickCount++
        }
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

    private fun tickSimulated() {
        val forceCount = packForces()
        val collisionGrid = if (store.blockCollisionCount > 0) {
            CParticleBlockCollisionGridManager.gridFor(origin, blockCollisionRange)
        } else {
            null
        }
        val useGpu = CParticleCapabilities.useGpuSimulation()
        if (useGpu) {
            // compute 必须先看到本 tick 的死亡和新生成槽位，否则 CPU age 会领先 GPU 一 tick。
            if (store.killedCount > 0) {
                glBuffer.patchFlags(store.data, store.killedSlots, store.killedCount)
            }
            if (store.spawnedCount > 0) {
                glBuffer.uploadSlots(store.data, store.spawnedSlots, store.spawnedCount)
            }
        }
        if (useGpu &&
            CParticleGpuSimulator.simulate(this, packedForces, forceCount, collisionGrid)
        ) {
            store.tickAges(writeBufferAge = false)
            store.publishDynamicAges()
            store.clearSpawned()
            store.clearKilled()
            store.clearDirty()
        } else {
            // CPU: 模拟写回 SoA → 整段上传
            CParticleCpuSimulator.simulate(
                store, packedForces, forceCount,
                origin.x, origin.y, origin.z, speedLimit, collisionGrid
            )
            store.tickAges(writeBufferAge = true)
            store.publishDynamicAges()
            uploadDirty()
            store.clearSpawned()
            store.clearKilled()
        }
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
        uploadDirty()
        store.clearSpawned()
        store.clearKilled()
    }

    private fun rollPrevForUnwritten() {
        val d = store.data
        val bits = store.aliveBits
        val words = (store.highWater + 63) ushr 6
        for (w in 0 until words) {
            var b = bits[w]
            while (b != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(b)
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

    private fun packForces(): Int {
        val count = forces.size.coerceAtMost(CParticleForce.MAX_FORCES)
        java.util.Arrays.fill(packedForces, 0f)
        for (i in 0 until count) {
            forces[i].pack(packedForces, i * CParticleForce.STRIDE, origin)
        }
        return count
    }

    // ------------------------------------------------------------ 生命周期

    /** 首次使用时创建 GL 资源 (渲染线程) */
    fun ensureGl() {
        if (!glBuffer.initialized) {
            glBuffer.init()
            // 新缓冲: 把当前 CPU 侧数据整体上传 (含 shader 重载后重建的场景)
            if (store.highWater > 0) {
                glBuffer.uploadRange(store.data, 0, store.highWater - 1)
            }
        }
    }

    /** 清空全部粒子 (保留 GL 资源) */
    fun clearParticles() {
        val prevHigh = store.highWater
        store.clear()
        snapGroupTransform()
        settleTicks = 0
        lastDynamicPrepareFrame = Long.MIN_VALUE
        if (glBuffer.initialized && prevHigh > 0) {
            // 同步清掉 GPU 侧 alive 位
            glBuffer.uploadRange(store.data, 0, prevHigh - 1)
        }
    }

    /** 仅释放 GL 资源 (shader/资源重载时; CPU 数据保留, 下次 ensureGl 重传) */
    fun releaseGl() {
        glBuffer.release()
    }

    /** 彻底销毁 */
    fun release() {
        released = true
        store.clear()
        lastDynamicPrepareFrame = Long.MIN_VALUE
        glBuffer.release()
    }

    /** 粗可见性: 相机到 origin 距离 */
    fun isVisible(cameraPos: Vec3): Boolean {
        if (store.aliveCount == 0) return false
        val r = visibleRange + 64.0
        return cameraPos.distanceToSqr(origin) <= r * r
    }

    internal fun snapGroupTransform() {
        previousGroupTransform.set(groupTransform)
        currentGroupTransform.set(groupTransform)
    }
}

enum class CParticleSystemMode {
    /** 发射器语义: 力场驱动, fire-and-forget */
    SIMULATED,

    /** composition 语义: CPU 位置权威 + 句柄控制 + 整组变换 */
    SCRIPTED
}
