package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.ConstantFloatCurve
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.FloatCurve
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.random.Random

/**
 * 继承速度命令（Inherit Velocity）。
 *
 * 目标：
 * - 让粒子继承“发射源速度”或任意自定义速度源
 * - 支持一次性继承（INITIAL）和持续跟随（CURRENT）
 * - 支持按轴屏蔽、生命周期权重、空间转换、阻尼跟随
 *
 * 常见场景：
 * - 武器挥动拖尾：CURRENT + 适度 damping
 * - 喷射火花：INITIAL + 较高 multiplier
 */
class ParticleInheritVelocityCommand() : ParticleCommand {
    /**
     * 速度来源。
     *
     * 默认返回 `Vec3.ZERO`，通常你会传入：
     * - `Supplier { emitter.emitterVelocity }`
     * - 或任意自定义动态速度源
     */
    var source: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /**
     * 继承模式。
     *
     * - INITIAL：只在首次执行时继承，后续保持（或按 damping 衰减）
     * - CURRENT：每 tick 跟随最新来源速度
     */
    var mode: ParticleInheritMode = ParticleInheritMode.INITIAL

    /**
     * 继承倍率。
     *
     * - 1.0：等于来源速度
     * - <1.0：弱继承
     * - >1.0：强化继承
     */
    var multiplier: Double = 1.0

    /**
     * 轴向掩码（逐轴相乘）。
     *
     * 示例：
     * - (1,0,1)：忽略 Y 轴继承
     * - (0,1,0)：只继承竖直方向
     */
    var axisMask: Vec3 = Vec3(1.0, 1.0, 1.0)

    /**
     * 生命周期权重曲线。
     *
     * 采样值会乘到继承速度上：
     * - 1 -> 全量继承
     * - 0 -> 不继承
     */
    var overLifetime: FloatCurve = ConstantFloatCurve(1.0)

    /**
     * 阻尼强度（用于平滑/衰减继承速度）。
     *
     * - 0：不平滑，立即跟随
     * - >0：指数平滑，值越大变化越柔和
     */
    var damping: Double = 0.0

    /**
     * 继承速度长度上限。
     *
     * - <=0：不限制
     * - >0：超过则截断
     */
    var maxContributionSpeed: Double = 0.0

    /**
     * 继承向量所在空间。
     *
     * - WORLD：按世界轴解释
     * - LOCAL：按粒子当前旋转解释后再转到世界空间
     */
    var space: ParticleMotionSpace = ParticleMotionSpace.WORLD

    /**
     * 是否启用按粒子固定随机缩放。
     */
    var randomizePerParticle: Boolean = false

    /**
     * 随机缩放最小值（按轴独立采样）。
     */
    var randomScaleMin: Double = 1.0

    /**
     * 随机缩放最大值（按轴独立采样）。
     */
    var randomScaleMax: Double = 1.0

    /**
     * 随机种子偏移。
     */
    var randomSeedOffset: Int = 0

    private val stateKey = "inherit_velocity.state.${System.identityHashCode(this)}"
    private val randomScaleKey = "inherit_velocity.random_scale.${System.identityHashCode(this)}"

    private data class InheritState(
        var initialized: Boolean = false,
        var applied: Vec3 = Vec3.ZERO
    )

    private data class AxisRandomScale(
        val x: Double,
        val y: Double,
        val z: Double
    )

    /**
     * @param source 速度来源
     * @param mode 继承模式（INITIAL/CURRENT）
     * @param multiplier 继承倍率
     * @param axisMask 轴向掩码
     * @param overLifetime 生命周期权重曲线
     * @param damping 平滑阻尼
     * @param maxContributionSpeed 继承速度上限
     * @param space 继承空间（WORLD/LOCAL）
     */
    constructor(
        source: Supplier<Vec3>,
        mode: ParticleInheritMode = ParticleInheritMode.INITIAL,
        multiplier: Double = 1.0,
        axisMask: Vec3 = Vec3(1.0, 1.0, 1.0),
        overLifetime: FloatCurve = ConstantFloatCurve(1.0),
        damping: Double = 0.0,
        maxContributionSpeed: Double = 0.0,
        space: ParticleMotionSpace = ParticleMotionSpace.WORLD
    ) : this() {
        this.source = source
        this.mode = mode
        this.multiplier = multiplier
        this.axisMask = axisMask
        this.overLifetime = overLifetime
        this.damping = damping
        this.maxContributionSpeed = maxContributionSpeed
        this.space = space
    }

    /**
     * 便捷构造：直接继承指定发射器的每 tick 位移速度。
     */
    constructor(emitter: ClassParticleEmitters) : this(
        source = Supplier { emitter.emitterVelocity }
    )

    /**
     * 设置速度来源。
     */
    fun source(v: Supplier<Vec3>) = apply { source = v }

    /**
     * 设置继承模式。
     *
     * - INITIAL：一次继承
     * - CURRENT：持续跟随
     */
    fun mode(v: ParticleInheritMode) = apply { mode = v }

    /**
     * 设置继承倍率。
     */
    fun multiplier(v: Double) = apply { multiplier = v }

    /**
     * 设置轴向掩码。
     */
    fun axisMask(v: Vec3) = apply { axisMask = v }

    /**
     * 设置生命周期权重曲线。
     */
    fun overLifetime(v: FloatCurve) = apply { overLifetime = v }

    /**
     * 设置阻尼强度。
     */
    fun damping(v: Double) = apply { damping = v }

    /**
     * 设置继承速度上限。
     */
    fun maxContributionSpeed(v: Double) = apply { maxContributionSpeed = v }

    /**
     * 设置继承空间（WORLD/LOCAL）。
     */
    fun space(v: ParticleMotionSpace) = apply { space = v }

    /**
     * 设置是否启用按粒子固定随机缩放。
     */
    fun randomizePerParticle(v: Boolean) = apply { randomizePerParticle = v }

    /**
     * 设置随机缩放区间。
     */
    fun randomScale(min: Double, max: Double) = apply {
        randomScaleMin = min
        randomScaleMax = max
    }

    /**
     * 设置随机种子偏移。
     */
    fun randomSeedOffset(v: Int) = apply { randomSeedOffset = v }

    /**
     * 每 tick 应用继承速度。
     */
    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val state = particle.controler.bufferedData.getOrPut(stateKey) { InheritState() } as InheritState

        val t = normalizedLifetime(particle)
        val lifeMultiplier = overLifetime.sample(t)
        val randomScale = resolveRandomScale(particle)

        var target = source.get()
        target = applyMask(target)
        target = scaleAxis(target, randomScale)
        target = target.scale(multiplier * lifeMultiplier)
        target = transformBySpace(target, space, particle)
        target = clampLength(target, maxContributionSpeed)

        target = when (mode) {
            ParticleInheritMode.INITIAL -> {
                if (!state.initialized) {
                    state.initialized = true
                    target
                } else if (damping > 0.0) {
                    state.applied.scale(GraphMathHelper.expDampFactor(damping, 1.0))
                } else {
                    state.applied
                }
            }

            ParticleInheritMode.CURRENT -> {
                state.initialized = true
                if (damping > 0.0) {
                    val follow = 1.0 - GraphMathHelper.expDampFactor(damping, 1.0)
                    state.applied.add(target.subtract(state.applied).scale(follow.coerceIn(0.0, 1.0)))
                } else {
                    target
                }
            }
        }

        val delta = target.subtract(state.applied)
        if (delta.lengthSqr() > 1e-12) {
            data.velocity = data.velocity.add(delta)
        }
        state.applied = target
    }

    private fun applyMask(vec: Vec3): Vec3 {
        return Vec3(
            vec.x * axisMask.x,
            vec.y * axisMask.y,
            vec.z * axisMask.z
        )
    }

    private fun scaleAxis(vec: Vec3, scale: AxisRandomScale): Vec3 {
        return Vec3(vec.x * scale.x, vec.y * scale.y, vec.z * scale.z)
    }

    private fun normalizedLifetime(particle: ControlableParticle): Double {
        if (particle.lifetime <= 0) return 0.0
        return (particle.currentAge.toDouble() / particle.lifetime.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun transformBySpace(
        vec: Vec3,
        space: ParticleMotionSpace,
        particle: ControlableParticle
    ): Vec3 {
        if (space == ParticleMotionSpace.WORLD) return vec

        val q = Quaternionf().rotateXYZ(
            particle.currentPitch,
            particle.currentYaw,
            particle.currentRoll
        )
        val v = Vector3f(vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())
        v.rotate(q)
        return Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
    }

    private fun resolveRandomScale(particle: ControlableParticle): AxisRandomScale {
        if (!randomizePerParticle) {
            return AxisRandomScale(1.0, 1.0, 1.0)
        }
        val cached = particle.controler.bufferedData[randomScaleKey]
        if (cached is AxisRandomScale) {
            return cached
        }

        val minScale = minOf(randomScaleMin, randomScaleMax)
        val maxScale = maxOf(randomScaleMin, randomScaleMax)
        val random = Random(particle.controlUUID.hashCode() + randomSeedOffset)
        val fixed = if (maxScale - minScale <= 1e-9) {
            AxisRandomScale(minScale, minScale, minScale)
        } else {
            AxisRandomScale(
                random.nextDouble(minScale, maxScale),
                random.nextDouble(minScale, maxScale),
                random.nextDouble(minScale, maxScale)
            )
        }
        particle.controler.bufferedData[randomScaleKey] = fixed
        return fixed
    }

    private fun clampLength(vec: Vec3, maxLen: Double): Vec3 {
        if (maxLen <= 0.0) return vec
        val len = vec.length()
        if (len <= 1e-9 || len <= maxLen) return vec
        return vec.scale(maxLen / len)
    }
}
