package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 流场：根据粒子位置/时间生成一个“风向量”，并叠加到速度里。
 *
 * 这个实现是解析型流场（sin/cos），特点：
 * - 计算非常快
 * - 通过 frequency / amplitude / timeScale 能做出很多“丝带/涡流”效果
 */
class ParticleFlowFieldCommand() : ParticleCommand {

    /**
     * 振幅（每 tick 的速度增量幅度）
     *
     * - 0.01 ~ 0.1：微风（烟雾漂移）
     * - 0.1 ~ 0.8：明显流动（能看出纹理）
     * - >0.8：很强（容易乱飞，需要阻尼/限速）
     */
    var amplitude: Double = 0.15

    /**
     * 空间频率（越大纹理越密，越小越平缓）
     *
     * - 0.05 ~ 0.2：大尺度流场
     * - 0.2 ~ 1.0：中小尺度卷曲
     */
    var frequency: Double = 0.25

    /**
     * 时间缩放（控制流场随时间变化速度）
     *
     * - 0：静态流场（像固定风）
     * - 0.02 ~ 0.2：缓慢变动
     * - >0.3：快速抖动/波动
     */
    var timeScale: Double = 0.06

    /**
     * 相位偏移（不同 emitter/不同组用不同 offset 会避免完全同步）
     */
    var phaseOffset: Double = 0.0

    /**
     * 是否使用世界坐标偏移（让同一效果在不同位置也不完全一致）
     */
    var worldOffset: Vec3 = Vec3.ZERO

    constructor(
        amplitude: Double = 0.15,
        frequency: Double = 0.25,
        timeScale: Double = 0.06,
        phaseOffset: Double = 0.0,
        worldOffset: Vec3 = Vec3.ZERO,
    ) : this() {
        this.amplitude = amplitude
        this.frequency = frequency
        this.timeScale = timeScale
        this.phaseOffset = phaseOffset
        this.worldOffset = worldOffset
    }

    /**
     * 振幅（每 tick 的速度增量幅度）
     *
     * - 0.01 ~ 0.1：微风（烟雾漂移）
     * - 0.1 ~ 0.8：明显流动（能看出纹理）
     * - >0.8：很强（容易乱飞，需要阻尼/限速）
     */
    fun amplitude(v: Double) = apply { amplitude = v }

    /**
     * 空间频率（越大纹理越密，越小越平缓）
     *
     * - 0.05 ~ 0.2：大尺度流场
     * - 0.2 ~ 1.0：中小尺度卷曲
     */
    fun frequency(v: Double) = apply { frequency = v }

    /**
     * 时间缩放（控制流场随时间变化速度）
     *
     * - 0：静态流场（像固定风）
     * - 0.02 ~ 0.2：缓慢变动
     * - >0.3：快速抖动/波动
     */
    fun timeScale(v: Double) = apply { timeScale = v }

    /**
     * 相位偏移（不同 emitter/不同组用不同 offset 会避免完全同步）
     */
    fun phaseOffset(v: Double) = apply { phaseOffset = v }

    /**
     * 是否使用世界坐标偏移（让同一效果在不同位置也不完全一致）
     */
    fun worldOffset(v: Vec3) = apply { worldOffset = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val p = particle.loc.add(worldOffset)
        val t = (particle.currentAge.toDouble() * timeScale) + phaseOffset

        // A cheap "curl-ish" analytic field
        val fx = sin((p.y + t) * frequency) + cos((p.z - t) * frequency)
        val fy = sin((p.z + t) * frequency) + cos((p.x + t) * frequency)
        val fz = sin((p.x - t) * frequency) + cos((p.y - t) * frequency)

        // normalize-ish (avoid sqrt cost, keep stable magnitude)
        val scale = 0.5
        val dv = Vec3(fx * scale, fy * scale, fz * scale).scale(amplitude)

        data.velocity = data.velocity.add(dv)
    }
}
