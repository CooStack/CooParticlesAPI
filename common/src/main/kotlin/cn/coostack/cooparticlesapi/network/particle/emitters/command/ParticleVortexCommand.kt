package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.max

/**
 * 漩涡：让粒子围绕轴旋转，并可向轴线“吸入”，以及沿轴“上升/下沉”。
 *
 * 常用组合：
 * - swirlStrength > 0 : 打旋
 * - radialPull > 0 : 往轴线收拢（像龙卷风）
 * - axialLift > 0 : 沿轴向上（像火焰/风柱）
 */
class ParticleVortexCommand() : ParticleCommand {

    /** 漩涡中心点（轴线穿过此点）。 */
    var center: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /** 轴方向（自动 normalize）。 */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /**
     * 切向旋转强度（每 tick 切向速度增量）
     *
     * - 0.1 ~ 0.8：柔和漩涡
     * - 0.8 ~ 3.0：明显龙卷感
     */
    var swirlStrength: Double = 0.8

    /**
     * 向轴线吸入的强度（每 tick 径向速度增量）
     *
     * - 0：不收拢（只打旋）
     * - 0.1 ~ 1.5：逐渐收拢形成“漏斗”
     */
    var radialPull: Double = 0.35

    /**
     * 沿轴方向的升力（每 tick 轴向速度增量）
     *
     * - >0：沿轴正方向上升
     * - <0：沿轴反方向下沉
     */
    var axialLift: Double = 0.0

    /** 衰减范围尺度（>0）。 */
    var range: Double = 10.0

    /** 衰减幂次（>=1）。 */
    var falloffPower: Double = 2.0

    /** 避免 r=0 的最小距离钳制 */
    var minDistance: Double = 0.2

    /**
     * @param center 漩涡中心（轴线穿过点）
     * @param axis 轴方向（自动 normalize）
     * @param swirlStrength 旋转强度（切向）
     * @param radialPull 径向吸入强度
     * @param axialLift 轴向升力
     * @param range 衰减范围尺度
     * @param falloffPower 衰减幂次
     * @param minDistance 最小距离钳制，避免抖动/爆炸
     */
    constructor(
        center: Supplier<Vec3> = Supplier { Vec3.ZERO },
        axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        swirlStrength: Double = 0.8,
        radialPull: Double = 0.35,
        axialLift: Double = 0.0,
        range: Double = 10.0,
        falloffPower: Double = 2.0,
        minDistance: Double = 0.2,
    ) : this() {
        this.center = center
        this.axis = axis
        this.swirlStrength = swirlStrength
        this.radialPull = radialPull
        this.axialLift = axialLift
        this.range = range
        this.falloffPower = falloffPower
        this.minDistance = minDistance
    }

    /** 漩涡中心点（轴线穿过此点）。 */
    fun center(v: Supplier<Vec3>) = apply { center = v }

    /** 轴方向（自动 normalize）。 */
    fun axis(v: Vec3) = apply { axis = v }

    /**
     * 切向旋转强度（每 tick 切向速度增量）
     *
     * - 0.1 ~ 0.8：柔和漩涡
     * - 0.8 ~ 3.0：明显龙卷感
     */
    fun swirlStrength(v: Double) = apply { swirlStrength = v }

    /**
     * 向轴线吸入的强度（每 tick 径向速度增量）
     *
     * - 0：不收拢（只打旋）
     * - 0.1 ~ 1.5：逐渐收拢形成“漏斗”
     */
    fun radialPull(v: Double) = apply { radialPull = v }

    /**
     * 沿轴方向的升力（每 tick 轴向速度增量）
     *
     * - >0：沿轴正方向上升
     * - <0：沿轴反方向下沉
     */
    fun axialLift(v: Double) = apply { axialLift = v }

    /** 衰减范围尺度（>0）。 */
    fun range(v: Double) = apply { range = v }

    /** 衰减幂次（>=1）。 */
    fun falloffPower(v: Double) = apply { falloffPower = v }

    /** 避免 r=0 的最小距离钳制 */
    fun minDistance(v: Double) = apply { minDistance = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val pos = particle.loc
        val ax = axis.normalize()

        // vector from center
        val r = pos.subtract(center.get())

        // remove axial component -> radial vector to axis line
        val axialComp = ax.scale(r.dot(ax))
        val radial = r.subtract(axialComp)

        var dist = radial.length()
        if (dist < 1e-9) dist = 0.0

        val d = max(dist, minDistance)
        val falloff = GraphMathHelper.inversePowerFalloff(d, range, falloffPower)

        // tangential direction
        var tangential = ax.cross(radial)
        val tLen = tangential.length()
        if (tLen > 1e-9) tangential = tangential.scale(1.0 / tLen)

        // radial inward direction
        var inward = Vec3.ZERO
        val rLen = radial.length()
        if (rLen > 1e-9) inward = radial.scale(-1.0 / rLen)

        val dv = tangential.scale(swirlStrength * falloff)
            .add(inward.scale(radialPull * falloff))
            .add(ax.scale(axialLift * falloff))

        data.velocity = data.velocity.add(dv)
    }
}
