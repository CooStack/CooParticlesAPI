package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.max

class ParticleOrbitCommand() : ParticleCommand {
    /** 轨道中心点（世界坐标），通过 Supplier 提供以支持动态更新 */
    var center: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /** 轨道所在平面的法向量（旋转轴），会在计算时进行 normalize */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /**
     * 目标轨道半径（世界单位）
     *
     * - 值越小：轨道越紧凑
     * - 值越大：粒子绕行范围越大
     */
    var radius: Double = 3.0

    /**
     * 角速度强度（每 tick 施加的切向速度增量）
     *
     * - 较小：缓慢绕行
     * - 较大：快速旋转（建议配合限速或阻尼）
     */
    var angularSpeed: Double = 0.35

    /**
     * 径向纠正强度（用于将粒子拉回目标半径）
     *
     * - 值越小：允许轨道半径有较大漂移
     * - 值越大：轨道更贴近目标半径，但过大可能导致抖动
     */
    var radialCorrect: Double = 0.25

    /**
     * 最小距离钳制，用于避免粒子过于接近轨道中心时出现数值异常（NaN）
     */
    var minDistance: Double = 0.2

    /**
     * 轨道模式
     *
     * - PHYSICAL：真实物理（默认）
     * - SPRING：弹簧收敛（推荐通用）
     * - SNAP：强制吸附（最稳定）
     */
    var mode: OrbitMode = OrbitMode.PHYSICAL

    /**
     * 仅在 PHYSICAL / SPRING 下生效：
     * 每 tick 最大径向修正量（防止启动时被拉飞）
     */
    var maxRadialStep: Double = 0.5

    constructor(
        center: Supplier<Vec3> = Supplier { Vec3.ZERO },
        axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        radius: Double = 3.0,
        angularSpeed: Double = 0.35,
        radialCorrect: Double = 0.25,
        minDistance: Double = 0.2,
        mode: OrbitMode = OrbitMode.PHYSICAL,
    ) : this() {
        this.center = center
        this.axis = axis
        this.radius = radius
        this.angularSpeed = angularSpeed
        this.radialCorrect = radialCorrect
        this.minDistance = minDistance
        this.mode = mode
    }

    /** 轨道中心点（世界坐标），通过 Supplier 提供以支持动态更新 */
    fun center(v: Supplier<Vec3>) = apply { center = v }

    /** 轨道所在平面的法向量（旋转轴），会在计算时进行 normalize */
    fun axis(v: Vec3) = apply { axis = v }

    /**
     * 目标轨道半径（世界单位）
     *
     * - 值越小：轨道越紧凑
     * - 值越大：粒子绕行范围越大
     */
    fun radius(v: Double) = apply { radius = v }

    /**
     * 角速度强度（每 tick 施加的切向速度增量）
     *
     * - 较小：缓慢绕行
     * - 较大：快速旋转（建议配合限速或阻尼）
     */
    fun angularSpeed(v: Double) = apply { angularSpeed = v }

    /**
     * 径向纠正强度（用于将粒子拉回目标半径）
     *
     * - 值越小：允许轨道半径有较大漂移
     * - 值越大：轨道更贴近目标半径，但过大可能导致抖动
     */
    fun radialCorrect(v: Double) = apply { radialCorrect = v }

    /**
     * 最小距离钳制，用于避免粒子过于接近轨道中心时出现数值异常（NaN）
     */
    fun minDistance(v: Double) = apply { minDistance = v }

    /**
     * 轨道模式
     *
     * - PHYSICAL：真实物理（默认）
     * - SPRING：弹簧收敛（推荐通用）
     * - SNAP：强制吸附（最稳定）
     */
    fun mode(v: OrbitMode) = apply { mode = v }

    /**
     * 仅在 PHYSICAL / SPRING 下生效：
     * 每 tick 最大径向修正量（防止启动时被拉飞）
     */
    fun maxRadialStep(v: Double) = apply { maxRadialStep = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val pos = particle.loc
        val ax = axis.normalize()
        val centerPos = center.get()

        val r0 = pos.subtract(centerPos)
        val axialComp = ax.scale(r0.dot(ax))
        var radial = r0.subtract(axialComp)

        var dist = radial.length()
        dist = max(dist, minDistance)

        radial = radial.scale(1.0 / dist)

        // 切向方向
        var tangential = ax.cross(radial)
        val tLen = tangential.length()
        if (tLen > 1e-9) tangential = tangential.scale(1.0 / tLen)

        val dvTan = tangential.scale(angularSpeed)
        val err = dist - radius

        when (mode) {
            OrbitMode.PHYSICAL -> {
                val raw = -err * radialCorrect
                val step = raw.coerceIn(-maxRadialStep, maxRadialStep)
                val dvRad = radial.scale(step)
                data.velocity = data.velocity.add(dvTan).add(dvRad)
            }

            OrbitMode.SPRING -> {
                // 二阶感：误差越大，拉力越强，但更温和
                val spring = -err * radialCorrect
                val step = spring.coerceIn(-maxRadialStep, maxRadialStep)
                val dvRad = radial.scale(step)
                data.velocity = data.velocity.add(dvTan).add(dvRad)
            }

            OrbitMode.SNAP -> {
                // 直接把粒子“吸附”到目标半径
                val targetPos = centerPos
                    .add(axialComp)
                    .add(radial.scale(radius))

                val snapVec = targetPos.subtract(pos)
                data.velocity = data.velocity.add(dvTan).add(snapVec.scale(radialCorrect))
            }
        }
    }
}
