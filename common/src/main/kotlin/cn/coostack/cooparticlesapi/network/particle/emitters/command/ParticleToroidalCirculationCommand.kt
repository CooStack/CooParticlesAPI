package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.extend.plus
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
 * 它会优先“扭转当前速度方向”，而不是把粒子硬拽成一个环，
 * 所以在有阻尼/减速时，卷动也会跟着自然变慢。
 * 它改的是粒子速度，不是 billboard 朝向。
 */
class ParticleToroidalCirculationCommand : ParticleCommand {
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

    /** 往翻卷带回带/导向的力度。主要用于带外把速度重新导回卷动区。 */
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
    ) {
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

        val radialSize = radialThickness.coerceAtLeast(1e-6)
        val axialSize = axialThickness.coerceAtLeast(1e-6)

        val localRadial = qr / radialSize
        val localAxial = axialDistance / axialSize
        val normalizedDistance = sqrt(localRadial * localRadial + localAxial * localAxial)

        val insideBand = normalizedDistance < 1.0
        val bandWeight = if (insideBand) smooth01(1.0 - normalizedDistance) else 0.0
        val outerDistance = (normalizedDistance - 1.0).coerceAtLeast(0.0)
        val captureWeight = if (outerDistance > 0.0) {
            1.0 / (1.0 + outerDistance * outerDistance * 4.0)
        } else {
            0.0
        }
        val lifeMul = if (useLifeCurve && particle.lifetime > 0) {
            (1.0 - particle.currentAge.toDouble() / particle.lifetime.toDouble()).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        if (lifeMul <= 1e-9) {
            return
        }

        val currentVelocity = if (data.velocity.lengthSqr() > 1e-12) data.velocity else particle.velocity
        val currentSpeed = currentVelocity.length()
        if (currentSpeed <= 1e-9) {
            return
        }
        val currentDir = currentVelocity.scale(1.0 / currentSpeed)

        val circulationVector = radialDir.scale(-localAxial)
            .add(ax.scale(localRadial))
        val toBandCenter = radialDir.scale(-localRadial).add(ax.scale(-localAxial))

        var desiredFlow = Vec3.ZERO

        val circulationWeight = bandWeight * lifeMul
        if (circulationStrength != 0.0 && circulationWeight > 1e-9 && circulationVector.lengthSqr() > 1e-12) {
            desiredFlow = desiredFlow.add(circulationVector.scale(circulationStrength * circulationWeight))
        }
        if (insideBand && outwardStrength != 0.0 && circulationWeight > 1e-9) {
            desiredFlow = desiredFlow.add(radialDir.scale(outwardStrength * circulationWeight))
        }
        if (insideBand && upwardStrength != 0.0 && circulationWeight > 1e-9) {
            desiredFlow = desiredFlow.add(ax.scale(upwardStrength * circulationWeight))
        }

        val captureMul = captureWeight * lifeMul
        if (followStrength != 0.0 && captureMul > 1e-9 && toBandCenter.lengthSqr() > 1e-12) {
            desiredFlow = desiredFlow.add(normalizeOrZero(toBandCenter).scale(followStrength * captureMul))
        }

        val desiredFlowLen = desiredFlow.length()
        if (desiredFlowLen <= 1e-9) {
            return
        }
        val desiredDir = desiredFlow.scale(1.0 / desiredFlowLen)
        val turnedDir = normalizeOrZero(currentDir.add(desiredDir.scale(desiredFlowLen)))
        if (turnedDir.lengthSqr() <= 1e-12) {
            return
        }

        var dv = turnedDir.scale(currentSpeed).subtract(currentVelocity)

        val dvLen = dv.length()
        if (maxStep > 0.0 && dvLen > maxStep) {
            dv = dv.scale(maxStep / dvLen)
        }

        data.velocity += dv
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
