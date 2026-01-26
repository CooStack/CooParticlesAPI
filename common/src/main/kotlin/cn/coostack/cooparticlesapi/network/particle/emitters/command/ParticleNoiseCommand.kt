package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.extend.multiply
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/**
 * 噪声扰动（Noise / Turbulence）
 *
 * 作用：在每个 tick 给粒子速度叠加一股“连续的风”，让轨迹更自然、更有生命感，
 * 常用于：烟雾/火焰抖动、能量流扰动、爆炸尘埃乱流、魔法粒子“活跃感”。
 *
 * @property strength
 * - 噪声加速度强度（每 tick 叠加到 velocity 的幅度）
 * - 越大：风越“猛”，轨迹更弯、更乱、更炸；过大时会出现抽搐/抖动、偏航严重、粒子群散架。
 * - 越小：扰动更轻微，只是细腻的呼吸感/轻微摆动，整体运动仍主要由初速度决定。
 * - 经验：火焰/烟雾中等，能量微扰偏小，爆炸尘埃可偏大但必须配合 drag 或 clampSpeed。
 *
 * @property frequency
 * - 空间频率（噪声随“空间位置”变化的密度，决定扰动“颗粒感”）
 * - 越大：噪声变化更快、更碎、更“抖”，局部细节更丰富；过大时会像高频抖动/电流乱颤。
 * - 越小：噪声变化更慢、更平滑，形成大尺度的“风带/云团”漂移；过小会显得太温柔、缺细节。
 * - 直觉：frequency 决定“风场纹理”的粗细（大=细碎，小=粗大块）。
 *
 * @property speed
 * - 时间滚动速度（噪声场随时间流动的快慢）
 * - 越大：风场变化更快，扰动更活跃，像湍急的气流；过大时会像噪声在闪烁，产生“乱抖”的观感。
 * - 越小：风场变化更慢，像缓慢流动的空气/云层；过小会显得“静止抖动”，缺少流体感。
 * - 直觉：speed 决定“风在动得有多快”（不是风的力量，力量是 strength）。
 *
 * @property affectY
 * - 垂直影响比例（对 Y 方向扰动的缩放）
 * - 越大：上下翻滚更明显，更“蓬/炸”，适合爆炸烟尘、魔法能量上涌；过大会显得漂浮、失重。
 * - 越小：更贴地/更平，垂直更稳定；常用于烟雾（避免上下乱跳）或贴地尘土。
 * - 常用：烟雾 0.3~0.7；能量上涌可 1.0~1.5；贴地尘土可 0.0~0.3。
 *
 * @property clampSpeed
 * - 限速（对最终速度做上限，防止速度叠加越来越快）
 * - 越大：允许粒子跑得更快，扰动会更“飘逸/发散”；过大时可能被 strength 一直堆出很高速度。
 * - 越小：更“黏”，粒子速度被压住，群体更稳更聚；过小时会把扰动的动态感也一起压扁。
 * - 直觉：clampSpeed 是“安全阀/天花板”，用来保证效果不会失控。
 *
 * @property useLifeCurve
 * - 是否按生命周期调制强度（把 strength 乘上寿命曲线）
 * - true：扰动会随生命周期变化（常见：出生强→死亡弱，或先弱后强），更像真实流体/能量变化。
 * - false：全程扰动强度恒定，更稳定可控，但可能显得“机械”，不够有呼吸感。
 * - 建议：大部分效果都开 true；只有想要“持续稳定噪声”时才关（例如长时间环境粒子）。
 */
class ParticleNoiseCommand(
    private var strength: Double = 0.03,
    private var frequency: Double = 0.15,
    private var speed: Double = 0.12,
    private var affectY: Double = 1.0,
    private var clampSpeed: Double = 0.8,
    private var useLifeCurve: Boolean = true,
) : ParticleCommand {
    constructor() : this(0.03, 0.15, 0.12, 1.0, 0.8, true)

    /**
     * 设置噪声加速度强度（每 tick 叠加到 velocity 的幅度）。
     *
     * - 越大：扰动更猛，轨迹更弯、更乱，需注意配合限速或阻尼
     * - 越小：扰动更轻微，仅提供细腻的呼吸感
     */
    fun strength(v: Double) = apply { strength = v }

    /**
     * 设置空间频率（噪声随空间位置变化的密度）。
     *
     * - 越大：扰动更细碎、更抖动
     * - 越小：扰动更平滑，形成大尺度风带
     */
    fun frequency(v: Double) = apply { frequency = v }

    /**
     * 设置时间滚动速度（噪声场随时间变化的快慢）。
     *
     * - 越大：风场变化更快，更活跃
     * - 越小：风场变化更慢，更沉稳
     */
    fun speed(v: Double) = apply { speed = v }

    /**
     * 设置垂直方向（Y 轴）的扰动影响比例。
     *
     * - 越大：上下翻滚更明显，更蓬松
     * - 越小：垂直更稳定，更贴地
     */
    fun affectY(v: Double) = apply { affectY = v }

    /**
     * 设置速度上限（最终 velocity 的最大长度）。
     *
     * - 用于防止噪声扰动持续叠加导致速度失控
     * - 值越大：运动更飘逸、发散
     * - 值越小：粒子群更稳、更聚
     */
    fun clampSpeed(v: Double) = apply { clampSpeed = v }

    /**
     * 设置是否按生命周期曲线调制扰动强度。
     *
     * - true：扰动随粒子生命周期变化，更自然、更有呼吸感
     * - false：全程恒定扰动，更稳定、可控
     */
    fun useLifeCurve(v: Boolean) = apply { useLifeCurve = v }


    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        // 0..1 生命周期进度
        val t = if (particle.lifetime > 0) particle.currentAge.toDouble() / particle.lifetime.toDouble() else 0.0

        // 取粒子的稳定 seed（一定要稳定）
        // 你可以换成 data.seed / spawnIndex / uuid hash
        val seed = particle.controlUUID.hashCode() // 如果没有 id，自己做一个

        // 时间输入：连续滚动
        val time = (particle.currentAge.toDouble()) * speed
        val pos = particle.loc
        // 噪声采样点：位置 * 频率 + time
        val p = (Vec3(pos.x, pos.y, pos.z) * this.frequency)
            .add(time, time * 0.7, time * 1.3)

        var amp = strength
        if (useLifeCurve) {
            // 这里你换成 data 的曲线：例如 data.strengthCurve.sample(t)
            // 我给默认：前强后弱（像烟）
            val lifeMul = (1.0 - t)
            amp *= lifeMul
        }

        val n = noiseVec3(p, seed)
        val dv = Vec3(n.x, n.y * affectY, n.z).multiply(amp)

        val v0 = data.velocity
        var v = v0.add(dv)

        // 限速（很重要）
        val sp2 = v.lengthSqr()
        val max2 = clampSpeed * clampSpeed
        if (sp2 > max2) {
            v = v.normalize().multiply(clampSpeed)
        }

        data.velocity = v
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun hash3(ix: Int, iy: Int, iz: Int, seed: Int): Double {
        // 纯 hash -> 0..1
        var n = ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 374761
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        // 转成 0..1
        return ((n and 0x7fffffff).toDouble() / 2147483647.0)
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

        return lerp(nxy0, nxy1, w) // 0..1
    }

    private fun noiseVec3(pos: Vec3, seed: Int): Vec3 {
        // 用不同 seed 偏移得到 xyz 三通道
        val nx = valueNoise3(pos, seed + 11) * 2 - 1
        val ny = valueNoise3(pos, seed + 23) * 2 - 1
        val nz = valueNoise3(pos, seed + 37) * 2 - 1
        val v = Vec3(nx, ny, nz)
        return if (v.lengthSqr() < 1e-8) Vec3.ZERO else v.normalize()
    }
}