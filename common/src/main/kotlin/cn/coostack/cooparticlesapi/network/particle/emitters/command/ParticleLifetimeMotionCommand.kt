package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.ConstantFloatCurve
import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.FloatCurve
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.random.Random

/**
 * 生命周期运动命令（Force over Lifetime + Velocity over Lifetime）。
 *
 * 你可以把它理解为 Unity 里两个模块的组合版：
 * - Force over Lifetime：每 tick 给 `data.velocity` 增加一段加速度
 * - Velocity over Lifetime：每 tick 按模式塑形速度（叠加/覆盖/乘法）
 *
 * 执行流程：
 * 1. 计算生命周期进度 `t = currentAge / lifetime`
 * 2. 采样 Force 曲线并叠加到当前速度
 * 3. 采样 Velocity 曲线并按 `velocityMode` 生效
 * 4. 可选：应用按粒子固定随机缩放（提升群体离散感）
 */
class ParticleLifetimeMotionCommand() : ParticleCommand {
    /**
     * Force 曲线 X 分量（每 tick 对 velocity.x 的增量）。
     *
     * - 正值：向世界/局部 +X 推
     * - 负值：向世界/局部 -X 推
     */
    var forceX: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Force 曲线 Y 分量（每 tick 对 velocity.y 的增量）。
     *
     * 常用于：
     * - 上升烟雾：前期正值，后期逐渐趋近 0
     * - 下坠火星：前期小正值，后期转负值
     */
    var forceY: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Force 曲线 Z 分量（每 tick 对 velocity.z 的增量）。
     */
    var forceZ: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Velocity 曲线 X 分量。
     *
     * 实际如何生效取决于 `velocityMode`：
     * - ADD：视作附加速度曲线
     * - OVERRIDE：视作目标速度
     * - MULTIPLY：视作按轴倍率
     */
    var velocityX: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Velocity 曲线 Y 分量。
     */
    var velocityY: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Velocity 曲线 Z 分量。
     */
    var velocityZ: FloatCurve = ConstantFloatCurve(0.0)

    /**
     * Force 曲线所在空间。
     *
     * - WORLD：曲线向量直接当世界方向
     * - LOCAL：曲线向量会按粒子当前 pitch/yaw/roll 旋转到世界方向
     */
    var forceSpace: ParticleMotionSpace = ParticleMotionSpace.WORLD

    /**
     * Velocity 曲线所在空间。
     *
     * - WORLD：速度塑形不随粒子朝向变化
     * - LOCAL：速度塑形会跟随粒子当前朝向
     */
    var velocitySpace: ParticleMotionSpace = ParticleMotionSpace.WORLD

    /**
     * Velocity 曲线作用模式。
     *
     * - ADD：把曲线速度作为“附加项”并做增量追踪
     * - OVERRIDE：把当前速度拉向曲线速度
     * - MULTIPLY：按轴对当前速度做乘法
     */
    var velocityMode: ParticleLifetimeVelocityMode = ParticleLifetimeVelocityMode.ADD

    /**
     * 是否启用“按粒子固定随机缩放”。
     *
     * 开启后每个粒子只在首次执行时采样一次随机倍率，
     * 后续 tick 重用同一倍率，保证轨迹连贯。
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
     *
     * 在相同参数下改变该值，可快速得到另一套离散分布。
     */
    var randomSeedOffset: Int = 0

    /**
     * 限制 Velocity over Lifetime 在单 tick 的最大速度改变量。
     *
     * - <= 0：不限制
     * - > 0：超过该值会被裁剪
     *
     * 作用：抑制曲线突变导致的速度跳变或爆速。
     */
    var maxVelocityDeltaPerTick: Double = 0.0

    private val velocityStateKey = "lifetime_motion.velocity_state.${System.identityHashCode(this)}"
    private val randomScaleKey = "lifetime_motion.random_scale.${System.identityHashCode(this)}"

    private data class VelocityState(var appliedVelocity: Vec3 = Vec3.ZERO)
    private data class AxisRandomScale(val x: Double, val y: Double, val z: Double)

    /**
     * @param forceX Force 曲线 X
     * @param forceY Force 曲线 Y
     * @param forceZ Force 曲线 Z
     * @param velocityX Velocity 曲线 X
     * @param velocityY Velocity 曲线 Y
     * @param velocityZ Velocity 曲线 Z
     * @param forceSpace Force 所在空间
     * @param velocitySpace Velocity 所在空间
     * @param velocityMode Velocity 生效模式
     */
    constructor(
        forceX: FloatCurve = ConstantFloatCurve(0.0),
        forceY: FloatCurve = ConstantFloatCurve(0.0),
        forceZ: FloatCurve = ConstantFloatCurve(0.0),
        velocityX: FloatCurve = ConstantFloatCurve(0.0),
        velocityY: FloatCurve = ConstantFloatCurve(0.0),
        velocityZ: FloatCurve = ConstantFloatCurve(0.0),
        forceSpace: ParticleMotionSpace = ParticleMotionSpace.WORLD,
        velocitySpace: ParticleMotionSpace = ParticleMotionSpace.WORLD,
        velocityMode: ParticleLifetimeVelocityMode = ParticleLifetimeVelocityMode.ADD
    ) : this() {
        this.forceX = forceX
        this.forceY = forceY
        this.forceZ = forceZ
        this.velocityX = velocityX
        this.velocityY = velocityY
        this.velocityZ = velocityZ
        this.forceSpace = forceSpace
        this.velocitySpace = velocitySpace
        this.velocityMode = velocityMode
    }

    /**
     * 一次性设置 Force 曲线 XYZ。
     *
     * 示例：
     * - `x=0,y>0,z=0`：纯上升
     * - `x/z 对称`：横向扩散
     */
    fun forceCurves(x: FloatCurve, y: FloatCurve, z: FloatCurve) = apply {
        forceX = x
        forceY = y
        forceZ = z
    }

    /**
     * 一次性设置 Velocity 曲线 XYZ。
     *
     * 建议：
     * - 做“轨道化速度”用 `OVERRIDE`
     * - 做“微调补偿”用 `ADD`
     */
    fun velocityCurves(x: FloatCurve, y: FloatCurve, z: FloatCurve) = apply {
        velocityX = x
        velocityY = y
        velocityZ = z
    }

    /**
     * 设置 Force 曲线空间。
     *
     * - WORLD：固定世界方向
     * - LOCAL：跟随粒子朝向
     */
    fun forceSpace(v: ParticleMotionSpace) = apply { forceSpace = v }

    /**
     * 设置 Velocity 曲线空间。
     *
     * - WORLD：常用于全局风场/轨道
     * - LOCAL：常用于“朝前推进”类效果
     */
    fun velocitySpace(v: ParticleMotionSpace) = apply { velocitySpace = v }

    /**
     * 设置 Velocity 曲线作用模式。
     *
     * - ADD / OVERRIDE / MULTIPLY
     */
    fun velocityMode(v: ParticleLifetimeVelocityMode) = apply { velocityMode = v }

    /**
     * 设置是否启用按粒子固定随机缩放。
     */
    fun randomizePerParticle(v: Boolean) = apply { randomizePerParticle = v }

    /**
     * 设置随机缩放区间。
     *
     * - 当 `min == max` 时等价于固定倍率
     * - 区间越大，离散感越强，但整体可控性会下降
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
     * 设置 Velocity 曲线每 tick 最大速度改变量。
     */
    fun maxVelocityDeltaPerTick(v: Double) = apply { maxVelocityDeltaPerTick = v }

    /**
     * 每 tick 应用生命周期运动。
     */
    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val t = normalizedLifetime(particle)
        val randomScale = resolveRandomScale(particle)

        val force = transformBySpace(
            scaleAxis(sampleForce(t), randomScale),
            forceSpace,
            particle
        )
        if (force.lengthSqr() > 1e-12) {
            data.velocity = data.velocity.add(force)
        }

        val sampledVelocity = transformBySpace(
            scaleAxis(sampleVelocity(t), randomScale),
            velocitySpace,
            particle
        )
        when (velocityMode) {
            ParticleLifetimeVelocityMode.ADD -> applyVelocityAdditive(data, particle, sampledVelocity)
            ParticleLifetimeVelocityMode.OVERRIDE -> {
                particle.controler.bufferedData.remove(velocityStateKey)
                var target = sampledVelocity
                if (maxVelocityDeltaPerTick > 0.0) {
                    val delta = clampLength(target.subtract(data.velocity), maxVelocityDeltaPerTick)
                    target = data.velocity.add(delta)
                }
                data.velocity = target
            }

            ParticleLifetimeVelocityMode.MULTIPLY -> {
                particle.controler.bufferedData.remove(velocityStateKey)
                val current = data.velocity
                var target = Vec3(
                    current.x * sampledVelocity.x,
                    current.y * sampledVelocity.y,
                    current.z * sampledVelocity.z
                )
                if (maxVelocityDeltaPerTick > 0.0) {
                    val delta = clampLength(target.subtract(current), maxVelocityDeltaPerTick)
                    target = current.add(delta)
                }
                data.velocity = target
            }
        }
    }

    private fun applyVelocityAdditive(
        data: ControlableParticleData,
        particle: ControlableParticle,
        sampledVelocity: Vec3
    ) {
        val state = particle.controler.bufferedData.getOrPut(velocityStateKey) { VelocityState() } as VelocityState
        val delta = clampLength(sampledVelocity.subtract(state.appliedVelocity), maxVelocityDeltaPerTick)
        if (delta.lengthSqr() > 1e-12) {
            data.velocity = data.velocity.add(delta)
        }
        state.appliedVelocity = state.appliedVelocity.add(delta)
    }

    private fun sampleForce(t: Double): Vec3 {
        return Vec3(
            forceX.sample(t),
            forceY.sample(t),
            forceZ.sample(t)
        )
    }

    private fun sampleVelocity(t: Double): Vec3 {
        return Vec3(
            velocityX.sample(t),
            velocityY.sample(t),
            velocityZ.sample(t)
        )
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

    private fun scaleAxis(vec: Vec3, scale: AxisRandomScale): Vec3 {
        return Vec3(vec.x * scale.x, vec.y * scale.y, vec.z * scale.z)
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
