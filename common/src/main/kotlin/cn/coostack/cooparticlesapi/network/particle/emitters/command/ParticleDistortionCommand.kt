package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.floor

class ParticleDistortionCommand() : ParticleCommand {
    var center: Supplier<Vec3> = Supplier { Vec3.ZERO }
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)
    var radius: Double = 3.0
    var radialStrength: Double = 0.35
    var axialStrength: Double = 0.25
    var tangentialStrength: Double = 0.0
    var frequency: Double = 0.25
    var timeScale: Double = 0.1
    var phaseOffset: Double = 0.0
    var followStrength: Double = 0.35
    var maxStep: Double = 0.6
    var baseAxial: Double = 0.0
    var seedOffset: Int = 0
    var useLifeCurve: Boolean = false

    /**
     * @param center 扰动中心点（轴线穿过的点，世界坐标）。
     * - 建议与发射器中心一致，这样“环”会被稳定地扭曲而不是整体漂移。
     * - 使用 Supplier 便于跟随移动的实体/点位（每 tick 取一次）。
     *
     * @param axis 扰动轴（环所在平面的法向/法线方向）。
     * - 会自动 normalize，零向量会回退到 (0,1,0)。
     * - (0,1,0) 表示环在 XZ 平面；(1,0,0) 表示环在 YZ 平面。
     *
     * @param radius 基础半径（未扰动前的目标环半径）。
     * - 设为发射器环半径时，扰动主要表现为“扭曲”而不是整体收缩/扩张。
     * - 过小会让环过紧，过大则偏离发射器形状。
     *
     * @param radialStrength 径向扰动强度（沿“半径方向”的噪声幅度）。
     * - 控制“鼓包/凹陷”的程度，值越大环越不规则。
     * - 设为 0 表示不做径向扭曲，只保留其它方向扰动。
     *
     * @param axialStrength 轴向扰动强度（沿“轴线方向”的噪声幅度）。
     * - 控制环在法向上的起伏（像波浪上下抖动）。
     * - 设为 0 则保持在同一平面内。
     *
     * @param tangentialStrength 切向扰动强度（沿“绕环方向”的噪声幅度）。
     * - 会让粒子沿环方向滑移，产生“扭拧/拉扯”的感觉。
     * - 设为 0 可避免环周向被拉散。
     *
     * @param frequency 空间频率（噪声随位置变化的密度）。
     * - 越大越“细碎/抖动”，越小越“平滑/大块”。
     * - 影响扭曲的纹理尺度。
     *
     * @param timeScale 时间变化速度（噪声随时间滚动的快慢）。
     * - 0 表示静态扭曲；越大越“流动/抖动”。
     * - 过大可能出现闪烁感。
     *
     * @param phaseOffset 相位偏移（用来错开不同发射器/批次的噪声）。
     * - 不改变强度，只改变噪声的“起点”。
     *
     * @param followStrength 追随强度（粒子被拉回扭曲环的力度）。
     * - 越大环越“紧”，不易发散；越小越“松”，容易漂离形状。
     *
     * @param maxStep 单 tick 最大修正步长（对速度增量做上限）。
     * - 防止拉回过猛导致抖动或穿透。
     * - <=0 表示不限制。
     *
     * @param baseAxial 基础轴向偏移（整体沿轴线的固定偏移量）。
     * - 用于把整个环抬高/降低或制作“偏心”的扭曲面。
     *
     * @param seedOffset 噪声种子偏移（同一效果的“变体”）。
     * - 相同 seedOffset 会得到相同扭曲纹理；不同值能做多种随机样式。
     *
     * @param useLifeCurve 是否按生命周期衰减扰动强度。
     * - true：出生强、死亡弱，更自然。
     * - false：全生命周期强度恒定，更稳定。
     */
    constructor(
        center: Supplier<Vec3> = Supplier { Vec3.ZERO },
        axis: Vec3 = Vec3(0.0, 1.0, 0.0),
        radius: Double = 3.0,
        radialStrength: Double = 0.35,
        axialStrength: Double = 0.25,
        tangentialStrength: Double = 0.0,
        frequency: Double = 0.25,
        timeScale: Double = 0.1,
        phaseOffset: Double = 0.0,
        followStrength: Double = 0.35,
        maxStep: Double = 0.6,
        baseAxial: Double = 0.0,
        seedOffset: Int = 0,
        useLifeCurve: Boolean = false,
    ) : this() {
        this.center = center
        this.axis = axis
        this.radius = radius
        this.radialStrength = radialStrength
        this.axialStrength = axialStrength
        this.tangentialStrength = tangentialStrength
        this.frequency = frequency
        this.timeScale = timeScale
        this.phaseOffset = phaseOffset
        this.followStrength = followStrength
        this.maxStep = maxStep
        this.baseAxial = baseAxial
        this.seedOffset = seedOffset
        this.useLifeCurve = useLifeCurve
    }

    /**
     * 设置扰动中心点（轴线穿过的点，世界坐标）。
     *
     * - 建议与发射器中心一致，保证环形整体不漂移。
     * - Supplier 可用于跟随移动对象，每 tick 自动读取最新位置。
     */
    fun center(v: Supplier<Vec3>) = apply { center = v }

    /**
     * 设置扰动轴（环所在平面的法线方向）。
     *
     * - 会在计算时 normalize。
     * - (0,1,0) 为 XZ 平面，(1,0,0) 为 YZ 平面。
     */
    fun axis(v: Vec3) = apply { axis = v }

    /**
     * 设置基础半径（未扰动前的目标环半径）。
     *
     * - 与发射器环半径一致时最稳定。
     * - 过小会让环“收紧”，过大可能偏离发射器形状。
     */
    fun radius(v: Double) = apply { radius = v }

    /**
     * 设置径向扰动强度（沿半径方向的扭曲幅度）。
     *
     * - 越大越“鼓包/凹陷”，环越不规则。
     * - 设为 0 可保持圆环半径稳定。
     */
    fun radialStrength(v: Double) = apply { radialStrength = v }

    /**
     * 设置轴向扰动强度（沿轴线方向的起伏幅度）。
     *
     * - 控制环在法向上的上下波动。
     * - 设为 0 则保持在同一平面内。
     */
    fun axialStrength(v: Double) = apply { axialStrength = v }

    /**
     * 设置切向扰动强度（沿环方向的滑移幅度）。
     *
     * - 让粒子沿环周向“拉扯/扭拧”。
     * - 过大可能导致环被拉散。
     */
    fun tangentialStrength(v: Double) = apply { tangentialStrength = v }

    /**
     * 设置空间频率（噪声随位置变化的密度）。
     *
     * - 越大越细碎、越抖；越小越平滑、越大块。
     * - 决定扭曲纹理的尺度。
     */
    fun frequency(v: Double) = apply { frequency = v }

    /**
     * 设置时间变化速度（噪声滚动速度）。
     *
     * - 0 表示静态扭曲。
     * - 越大越活跃，但过大容易“闪烁抖动”。
     */
    fun timeScale(v: Double) = apply { timeScale = v }

    /**
     * 设置相位偏移（错开不同发射器/批次的噪声）。
     *
     * - 不影响强度，只改变噪声起点。
     * - 用于避免多个环“同步抖动”。
     */
    fun phaseOffset(v: Double) = apply { phaseOffset = v }

    /**
     * 设置追随强度（粒子被拉回扭曲环的力度）。
     *
     * - 越大越“紧”，不易发散。
     * - 越小越“松”，允许更自由的漂移。
     */
    fun followStrength(v: Double) = apply { followStrength = v }

    /**
     * 设置单 tick 最大修正步长（速度增量上限）。
     *
     * - 防止拉回过猛导致抖动或穿透。
     * - <=0 表示不限制。
     */
    fun maxStep(v: Double) = apply { maxStep = v }

    /**
     * 设置基础轴向偏移（整体沿轴线的固定偏移量）。
     *
     * - 可把环整体抬高/降低。
     * - 配合轴向扰动可做“偏心/倾移”的扭曲环。
     */
    fun baseAxial(v: Double) = apply { baseAxial = v }

    /**
     * 设置噪声种子偏移（同效果的随机变体）。
     *
     * - 相同 seedOffset -> 相同扭曲纹理。
     * - 不同 seedOffset -> 不同随机外观。
     */
    fun seedOffset(v: Int) = apply { seedOffset = v }

    /**
     * 设置是否按生命周期衰减扰动强度。
     *
     * - true：出生强、死亡弱，更自然。
     * - false：全生命周期恒定，更稳定。
     */
    fun useLifeCurve(v: Boolean) = apply { useLifeCurve = v }

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        val centerPos = center.get()
        val ax = safeNormalize(axis)
        val pos = particle.loc
        val r = pos.subtract(centerPos)
        val axialComp = ax.scale(r.dot(ax))
        var radial = r.subtract(axialComp)
        var radialLen = radial.length()
        if (radialLen < 1e-9) {
            radial = anyPerp(ax)
            radialLen = 1.0
        } else {
            radial = radial.scale(1.0 / radialLen)
        }

        var tangent = ax.cross(radial)
        val tLen = tangent.length()
        tangent = if (tLen < 1e-9) anyPerp(ax) else tangent.scale(1.0 / tLen)

        val lifeT = if (particle.lifetime > 0) {
            particle.currentAge.toDouble() / particle.lifetime.toDouble()
        } else {
            0.0
        }
        val lifeMul = if (useLifeCurve) (1.0 - lifeT).coerceIn(0.0, 1.0) else 1.0

        val time = particle.currentAge.toDouble() * timeScale + phaseOffset
        val local = pos.subtract(centerPos)
        val p = local.scale(frequency).add(time, time * 0.7, time * 1.3)
        val seed = particle.controlUUID.hashCode() + seedOffset

        val nr = noise(p, seed + 11)
        val na = noise(p, seed + 23)
        val nt = noise(p, seed + 37)

        val targetRadius = (radius + nr * radialStrength * lifeMul).coerceAtLeast(0.0)
        val targetAxial = baseAxial + na * axialStrength * lifeMul
        val targetTangential = nt * tangentialStrength * lifeMul

        val targetPos = centerPos
            .add(ax.scale(targetAxial))
            .add(radial.scale(targetRadius))
            .add(tangent.scale(targetTangential))

        var dv = targetPos.subtract(pos).scale(followStrength)
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

    private fun anyPerp(axis: Vec3): Vec3 {
        val ref = if (abs(axis.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val perp = axis.cross(ref)
        val len = perp.length()
        return if (len < 1e-9) Vec3(0.0, 0.0, 1.0) else perp.scale(1.0 / len)
    }

    private fun noise(p: Vec3, seed: Int): Double {
        return valueNoise3(p, seed) * 2.0 - 1.0
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun hash3(ix: Int, iy: Int, iz: Int, seed: Int): Double {
        var n = ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 374761
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toDouble() / 2147483647.0
    }

    private fun valueNoise3(p: Vec3, seed: Int): Double {
        val x0 = floor(p.x).toInt()
        val y0 = floor(p.y).toInt()
        val z0 = floor(p.z).toInt()
        val fx = p.x - x0
        val fy = p.y - y0
        val fz = p.z - z0

        val u = fade(fx)
        val v = fade(fy)
        val w = fade(fz)

        fun h(dx: Int, dy: Int, dz: Int) = hash3(x0 + dx, y0 + dy, z0 + dz, seed)

        val n000 = h(0, 0, 0)
        val n100 = h(1, 0, 0)
        val n010 = h(0, 1, 0)
        val n110 = h(1, 1, 0)
        val n001 = h(0, 0, 1)
        val n101 = h(1, 0, 1)
        val n011 = h(0, 1, 1)
        val n111 = h(1, 1, 1)

        val nx00 = lerp(n000, n100, u)
        val nx10 = lerp(n010, n110, u)
        val nx01 = lerp(n001, n101, u)
        val nx11 = lerp(n011, n111, u)

        val nxy0 = lerp(nx00, nx10, v)
        val nxy1 = lerp(nx01, nx11, v)

        return lerp(nxy0, nxy1, w)
    }
}
