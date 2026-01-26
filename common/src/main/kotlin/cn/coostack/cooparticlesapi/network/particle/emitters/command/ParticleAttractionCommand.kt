package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.max

/**
 * 吸引/排斥力（指向目标点的加速度）
 *
 * - strength > 0：吸引（往目标点拉）
 * - strength < 0：排斥（从目标点推开）
 * - falloff 决定“近强远弱”的衰减方式
 */
class ParticleAttractionCommand() : ParticleCommand {

    /** 目标点（世界坐标）。 */
    var target: Supplier<Vec3> = Supplier { Vec3.ZERO }

    /**
     * 强度（每 tick 的速度增量基准）。
     *
     * - 0.1 ~ 0.5：轻微牵引（像磁力）
     * - 0.5 ~ 3.0：明显吸附（像黑洞）
     * - >3.0：非常暴力（需要配合 speedLimit / damping）
     *
     * 负数则变成排斥力。
     */
    var strength: Double = 0.8

    /**
     * 有效范围尺度（>0）。
     *
     * - 越大：衰减越慢，远处也能感受到吸力
     * - 越小：只有靠近目标才明显
     */
    var range: Double = 8.0

    /**
     * 衰减幂次（>=1）。
     *
     * - 1：衰减较缓
     * - 2：更像“平方反比”趋势
     * - 3：近距离极强，远处几乎无
     */
    var falloffPower: Double = 2.0

    /**
     * 最小距离钳制（避免距离=0导致 NaN 或爆炸加速度）。
     *
     * - 建议 0.1 ~ 1.0
     */
    var minDistance: Double = 0.25

    /**
     * @param target 目标点（世界坐标）
     * @param strength 强度（>0吸引 <0排斥）
     * @param range 范围尺度，越大影响越远
     * @param falloffPower 衰减幂次（>=1）
     * @param minDistance 最小距离钳制，防止爆炸/NaN
     */
    constructor(
        target: Supplier<Vec3> = Supplier { Vec3.ZERO },
        strength: Double = 0.8,
        range: Double = 8.0,
        falloffPower: Double = 2.0,
        minDistance: Double = 0.25,
    ) : this() {
        this.target = target
        this.strength = strength
        this.range = range
        this.falloffPower = falloffPower
        this.minDistance = minDistance
    }

    /** 目标点（世界坐标）。 */
    fun target(v: Supplier<Vec3>) = apply { target = v }

    /**
     * 强度（每 tick 的速度增量基准）。
     *
     * - 0.1 ~ 0.5：轻微牵引（像磁力）
     * - 0.5 ~ 3.0：明显吸附（像黑洞）
     * - >3.0：非常暴力（需要配合 speedLimit / damping）
     *
     * 负数则变成排斥力。
     */
    fun strength(v: Double) = apply { strength = v }

    /**
     * 有效范围尺度（>0）。
     *
     * - 越大：衰减越慢，远处也能感受到吸力
     * - 越小：只有靠近目标才明显
     */
    fun range(v: Double) = apply { range = v }

    /**
     * 衰减幂次。
     *
     * - 1：衰减较缓
     * - 2：更像“平方反比”趋势
     * - 3：近距离极强，远处几乎无
     */
    fun falloffPower(v: Double) = apply { falloffPower = v }

    /**
     * 最小距离钳制（避免距离=0导致 NaN 或爆炸加速度）。
     *
     * - 建议 0.1 ~ 1.0
     */
    fun minDistance(v: Double) = apply { minDistance = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val pos = particle.loc
        val dir = target.get().subtract(pos)
        var d = dir.length()
        if (d < 1e-9) return
        d = max(d, minDistance)

        val falloff = GraphMathHelper.inversePowerFalloff(d, range, falloffPower)
        val dv = dir.normalize().scale(strength * falloff)

        data.velocity = data.velocity.add(dv)
    }
}