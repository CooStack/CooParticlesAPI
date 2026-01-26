package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.world.phys.Vec3

/**
 * 空气阻尼（Drag）
 *
 * 每 tick 把速度乘上 (1 - drag)，让粒子慢下来、更“黏”。
 */
class ParticleDragCommand() : ParticleCommand {
    /**
     * 阻尼强度（>=0）。
     *
     * - 0.0：完全不阻尼
     * - 0.05 ~ 0.2：轻微“粘滞感”（漂浮粒子）
     * - 0.2 ~ 1.0：明显刹车（碎屑、火花）
     * - >1.0：非常快停（像在胶水里）
     */
    var damping: Double = 0.15

    /**
     * 最小速度阈值（<=0 表示不使用）。
     *
     * - 用来避免速度无限接近 0 时还在做无意义计算/抖动
     */
    var minSpeed: Double = 0.0

    /**
     * 额外的线性阻力（每 tick 额外减去 speed * linear）
     *
     * - 推荐保持 0，除非你想要“低速更粘”的感觉
     */
    var linear: Double = 0.0

    /**
     * @param damping 阻尼强度（>=0），越大速度衰减越快
     * @param minSpeed 低于该速度则直接归零（<=0 不启用）
     * @param linear 线性阻力项（建议 0）
     */
    constructor(
        damping: Double = 0.15,
        minSpeed: Double = 0.0,
        linear: Double = 0.0,
    ) : this() {
        this.damping = damping
        this.minSpeed = minSpeed
        this.linear = linear
    }

    /**
     * 阻尼强度（>=0）。
     *
     * - 0.0：完全不阻尼
     * - 0.05 ~ 0.2：轻微“粘滞感”（漂浮粒子）
     * - 0.2 ~ 1.0：明显刹车（碎屑、火花）
     * - >1.0：非常快停（像在胶水里）
     */
    fun damping(v: Double) = apply { damping = v }

    /**
     * 最小速度阈值（<=0 表示不使用）。
     *
     * - 用来避免速度无限接近 0 时还在做无意义计算/抖动
     */
    fun minSpeed(v: Double) = apply { minSpeed = v }

    /**
     * 额外的线性阻力（每 tick 额外减去 speed * linear）
     *
     * - 推荐保持 0，除非你想要“低速更粘”的感觉
     */
    fun linear(v: Double) = apply { linear = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        var v = data.velocity
        val speed = v.length()
        if (minSpeed > 0 && speed <= minSpeed) {
            data.velocity = Vec3.ZERO
            return
        }

        // exp damping
        val factor = GraphMathHelper.expDampFactor(damping, 1.0)
        v = v.scale(factor)

        // optional linear drag
        if (linear > 0.0) {
            val s2 = v.length()
            if (s2 > 1e-9) {
                val shrink = (1.0 - linear).coerceIn(0.0, 1.0)
                v = v.scale(shrink)
            }
        }
        data.velocity = v
    }
}