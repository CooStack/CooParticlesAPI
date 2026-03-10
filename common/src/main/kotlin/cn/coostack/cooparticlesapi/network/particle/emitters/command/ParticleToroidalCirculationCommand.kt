package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 环面回流场。
 *
 * 在一圈局部区域里制造“翻卷”轨迹，适合蘑菇云帽檐、烟团边缘这类效果。
 * 它改的是粒子速度，不是 billboard 朝向。
 */
class ParticleToroidalCirculationCommand() : ParticleCommand {
    /** 环流中心。做蘑菇云时一般填帽子中心。 */
    var center: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /** 主轴，默认 Y 轴。 */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /** 主半径，也就是翻卷带离中心的距离。 */
    var ringRadius: Double = 3.0

    /** 径向厚度。 */
    var radialThickness: Double = 1.2

    /** 轴向厚度。 */
    var axialThickness: Double = 0.8

    /** 翻卷力度，负数表示反向。 */
    var circulationStrength: Double = 0.35

    /** 向外撑开的附加力度。 */
    var outwardStrength: Double = 0.0

    /** 向上抬的附加力度。 */
    var upwardStrength: Double = 0.0

    /** 往翻卷带中心回拉的力度。太小容易散。 */
    var followStrength: Double = 0.12

    /** 单 tick 最大修正量。<=0 不限制。 */
    var maxStep: Double = 0.6

    /** 是否按生命周期减弱。 */
    var useLifeCurve: Boolean = false

    constructor(
        center: Supplier<Vec3> = Supplier { Vec3.ZERO },
        axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        ringRadius: Double = 3.0,
        radialThickness: Double = 1.2,
        axialThickness: Double = 0.8,
        circulationStrength: Double = 0.35,
        outwardStrength: Double = 0.0,
        upwardStrength: Double = 0.0,
        followStrength: Double = 0.12,
        maxStep: Double = 0.6,
        useLifeCurve: Boolean = false,
    ) : this() {
        this.center = center
        this.axis = axis
        this.ringRadius = ringRadius
        this.radialThickness = radialThickness
        this.axialThickness = axialThickness
        this.circulationStrength = circulationStrength
        this.outwardStrength = outwardStrength
        this.upwardStrength = upwardStrength
        this.followStrength = followStrength
        this.maxStep = maxStep
        this.useLifeCurve = useLifeCurve
    }

    fun center(v: Supplier<Vec3>) = apply { center = v }

    fun center(v: Vec3) = apply { center = Supplier { v } }

    fun center(x: Double, y: Double, z: Double) = apply { center = Supplier { Vec3(x, y, z) } }

    fun axis(v: Vec3) = apply { axis = v }

    fun axis(x: Double, y: Double, z: Double) = apply { axis = Vec3(x, y, z) }

    fun ringRadius(v: Double) = apply { ringRadius = v }

    fun radialThickness(v: Double) = apply { radialThickness = v }

    fun axialThickness(v: Double) = apply { axialThickness = v }

    /** 同时设置两个厚度，方便网页只留一个输入框。 */
    fun thickness(v: Double) = apply {
        radialThickness = v
        axialThickness = v
    }

    fun circulationStrength(v: Double) = apply { circulationStrength = v }


    fun outwardStrength(v: Double) = apply { outwardStrength = v }

    fun upwardStrength(v: Double) = apply { upwardStrength = v }

    fun followStrength(v: Double) = apply { followStrength = v }

    fun maxStep(v: Double) = apply { maxStep = v }

    fun useLifeCurve(v: Boolean) = apply { useLifeCurve = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val centerPos = center.get()
        val ax = safeNormalize(axis)
        val pos = particle.loc
        val rel = pos.subtract(centerPos)

        val axialDistance = rel.dot(ax)
        val planar = rel.subtract(ax.scale(axialDistance))
        val planarLen = planar.length()

        val radialDir = if (planarLen < 1e-9) {
            anyPerp(ax)
        } else {
            planar.scale(1.0 / planarLen)
        }

        val qr = planarLen - ringRadius
        val qh = axialDistance

        val radialSize = radialThickness.coerceAtLeast(1e-6)
        val axialSize = axialThickness.coerceAtLeast(1e-6)

        val normalizedDistance = sqrt(
            (qr * qr) / (radialSize * radialSize) +
                (qh * qh) / (axialSize * axialSize)
        )
        if (normalizedDistance >= 1.0) {
            return
        }

        val bandWeight = smooth01(1.0 - normalizedDistance)
        val lifeMul = if (useLifeCurve && particle.lifetime > 0) {
            (1.0 - particle.currentAge.toDouble() / particle.lifetime.toDouble()).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val weight = bandWeight * lifeMul
        if (weight <= 1e-9) {
            return
        }

        val circulationVector = radialDir.scale(-qh / axialSize)
            .add(ax.scale(qr / radialSize))
        val circulationDir = normalizeOrZero(circulationVector)

        val toBandCenter = radialDir.scale(-qr).add(ax.scale(-qh))
        val followDir = normalizeOrZero(toBandCenter)

        var dv = Vec3.ZERO

        if (circulationDir.lengthSqr() > 1e-12) {
            dv = dv.add(circulationDir.scale(circulationStrength * weight))
        }
        if (outwardStrength != 0.0) {
            dv = dv.add(radialDir.scale(outwardStrength * weight))
        }
        if (upwardStrength != 0.0) {
            dv = dv.add(ax.scale(upwardStrength * weight))
        }
        if (followDir.lengthSqr() > 1e-12 && followStrength != 0.0) {
            dv = dv.add(followDir.scale(followStrength * weight))
        }

        val dvLen = dv.length()
        if (maxStep > 0.0 && dvLen > maxStep) {
            dv = dv.scale(maxStep / dvLen)
        }

        data.velocity = data.velocity.add(dv)
    }

    private fun safeNormalize(v: Vec3): Vec3 {
        val len = v.length()
        return if (len < 1e-9) Vec3(0.0, 1.0, 0.0) else v.scale(1.0 / len)
    }

    private fun normalizeOrZero(v: Vec3): Vec3 {
        val len = v.length()
        return if (len < 1e-9) Vec3.ZERO else v.scale(1.0 / len)
    }

    private fun anyPerp(axis: Vec3): Vec3 {
        val ref = if (abs(axis.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val perp = axis.cross(ref)
        val len = perp.length()
        return if (len < 1e-9) Vec3(0.0, 0.0, 1.0) else perp.scale(1.0 / len)
    }

    private fun smooth01(t: Double): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        return clamped * clamped * (3.0 - 2.0 * clamped)
    }
}
