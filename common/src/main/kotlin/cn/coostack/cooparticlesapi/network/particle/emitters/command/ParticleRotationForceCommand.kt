package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier

/**
 * 旋转力：让粒子绕某个轴产生切向速度（像被“搅拌”）。
 *
 * 典型效果：
 * - 火焰上升的旋涡感
 * - 魔法阵环绕
 * - 爆炸烟雾“打旋”
 */
class ParticleRotationForceCommand() : ParticleCommand {

    /** 旋转中心（世界坐标）。 */
    var center: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /**
     * 旋转轴（会被自动 normalize）。
     *
     * - (0,1,0) 绕 Y 轴旋转（最常用）
     * - (1,0,0) 绕 X
     * - (0,0,1) 绕 Z
     */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /**
     * 旋转强度（每 tick 切向速度增量）。
     *
     * - 0.05 ~ 0.3：轻微旋
     * - 0.3 ~ 2.0：明显旋
     * - >2.0：非常暴力（建议配合阻尼/限速）
     */
    var strength: Double = 0.35

    /**
     * 距离衰减范围（>0）。
     *
     * - 越大：远处仍然能被带动旋转
     * - 越小：只在中心附近打旋
     */
    var range: Double = 8.0

    /** 衰减幂次（>=1）。 */
    var falloffPower: Double = 2.0

    /**
     * @param center 旋转中心
     * @param axis 旋转轴（自动 normalize）
     * @param strength 旋转强度（每 tick 切向速度增量）
     * @param range 衰减范围尺度
     * @param falloffPower 衰减幂次（>=1）
     */
    constructor(
        center: Supplier<Vec3> = Supplier { Vec3.ZERO },
        axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        strength: Double = 0.35,
        range: Double = 8.0,
        falloffPower: Double = 2.0,
    ) : this() {
        this.center = center
        this.axis = axis
        this.strength = strength
        this.range = range
        this.falloffPower = falloffPower
    }

    fun center(v: Supplier<Vec3>) = apply { center = v }
    fun axis(v: Vec3) = apply { axis = v }
    fun strength(v: Double) = apply { strength = v }
    fun range(v: Double) = apply { range = v }
    fun falloffPower(v: Double) = apply { falloffPower = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val pos = particle.loc
        val r = pos.subtract(center.get())
        val dist = r.length()
        if (dist < 1e-9) return

        val ax = axis.normalize()
        // tangential direction: axis x r
        var t = ax.cross(r)
        val tLen = t.length()
        if (tLen < 1e-9) return
        t = t.scale(1.0 / tLen)

        val falloff = GraphMathHelper.inversePowerFalloff(dist, range, falloffPower)
        val dv = t.scale(strength * falloff)

        data.velocity = data.velocity.add(dv)
    }
}