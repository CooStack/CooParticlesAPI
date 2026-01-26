package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.extend.lengthCoerceAtMost
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.Math.pow
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 数学插值工具提供
 */
object GraphMathHelper {
    private val rng = java.util.Random()

    /**
     * gen <= min 返回 0.0
     * gen >= max 返回 1.0
     * gen in min .. max 返回0 .. 1的平滑插值
     */
    @JvmStatic
    fun smoothStep(min: Float, max: Float, gen: Vector3f): Vector3f {
        val gx =
            if (gen.x <= min) 0.0f else if (gen.x >= max) 1.0f else ((gen.x - min) / (max - min)).coerceIn(0.0f, 1.0f)
        val gy =
            if (gen.y <= min) 0.0f else if (gen.y >= max) 1.0f else ((gen.y - min) / (max - min)).coerceIn(0.0f, 1.0f)
        val gz =
            if (gen.z <= min) 0.0f else if (gen.z >= max) 1.0f else ((gen.z - min) / (max - min)).coerceIn(0.0f, 1.0f)
        val resX = gx.pow(2) * (3 - 2 * gx)
        val resY = gy.pow(2) * (3 - 2 * gy)
        val resZ = gz.pow(2) * (3 - 2 * gz)
        return Vector3f(resX, resY, resZ)
    }

    /**
     * gen <= min 返回 0.0
     * gen >= max 返回 1.0
     * gen in min .. max 返回0 .. 1的平滑插值
     */
    @JvmStatic
    fun smoothStep(min: Double, max: Double, gen: Double): Double {
        if (gen <= min) return 0.0
        if (gen >= max) return 1.0

        val x = ((gen - min) / (max - min)).coerceIn(0.0, 1.0)
        val res = x.pow(2) * (3 - 2 * x)
        return res
    }

    /**
     * gen <= min 返回 0.0
     * gen >= max 返回 1.0
     * gen in min .. max 返回0 .. 1的平滑插值
     */
    @JvmStatic
    fun smoothStep(min: Float, max: Float, gen: Float): Float {
        if (gen <= min) return 0.0f
        if (gen >= max) return 1.0f

        val x = ((gen - min) / (max - min)).coerceIn(0.0f, 1.0f)
        val res = x.pow(2) * (3 - 2 * x)
        return res
    }

    @JvmStatic
    fun mix(c1: Vec3, c2: Vec3, delta: Double): Vec3 {
        return Vec3(mix(c1.toVector3f(), c2.toVector3f(), delta))
    }

    @JvmStatic
    fun mix(c1: Vec3, c2: Vec3, delta: Float): Vec3 {
        return Vec3(mix(c1.toVector3f(), c2.toVector3f(), delta))
    }

    @JvmStatic
    fun mix(c1: Vector3f, c2: Vector3f, delta: Double): Vector3f {
        val x = lerp(delta, c1.x, c2.x)
        val y = lerp(delta, c1.y, c2.y)
        val z = lerp(delta, c1.z, c2.z)
        return Vector3f(x, y, z)
    }


    @JvmStatic
    fun mix(c1: Vector3f, c2: Vector3f, delta: Float): Vector3f {
        val x = lerp(delta, c1.x, c2.x)
        val y = lerp(delta, c1.y, c2.y)
        val z = lerp(delta, c1.z, c2.z)
        return Vector3f(x, y, z)
    }


    @JvmStatic
    fun lerp(delta: Vec3, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta.x, 0.0, stepX)
        val mixY = lerp(delta.y, 0.0, stepY)
        val mixZ = lerp(delta.z, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(delta: Float, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta, 0.0, stepX)
        val mixY = lerp(delta, 0.0, stepY)
        val mixZ = lerp(delta, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(delta: Float, min: RelativeLocation, max: RelativeLocation): RelativeLocation {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta, 0.0, stepX)
        val mixY = lerp(delta, 0.0, stepY)
        val mixZ = lerp(delta, 0.0, stepZ)
        return min.add(RelativeLocation(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(delta: Double, min: RelativeLocation, max: RelativeLocation): RelativeLocation {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta, 0.0, stepX)
        val mixY = lerp(delta, 0.0, stepY)
        val mixZ = lerp(delta, 0.0, stepZ)
        return min.add(RelativeLocation(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(delta: Float, min: Vector3f, max: Vector3f): Vector3f {
        return lerp(delta, Vec3(min), Vec3(max)).toVector3f()
    }

    @JvmStatic
    fun lerp(delta: Double, min: Vector3f, max: Vector3f): Vector3f {
        return lerp(delta, Vec3(min), Vec3(max)).toVector3f()
    }

    @JvmStatic
    fun lerp(delta: Double, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta, 0.0, stepX)
        val mixY = lerp(delta, 0.0, stepY)
        val mixZ = lerp(delta, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(delta: Vector3f, min: Vector3f, max: Vector3f): Vector3f {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(delta.x, 0f, stepX)
        val mixY = lerp(delta.y, 0f, stepY)
        val mixZ = lerp(delta.z, 0f, stepZ)
        return min.add(mixX, mixY, mixZ, Vector3f())
    }


    /**
     * step插值
     * 详细见glsl的step函数
     */
    fun step(limit: Double, enter: Double): Double {
        return if (limit > enter) 0.0 else 1.0
    }

    /**
     * step插值
     * 详细见glsl的step函数
     */
    fun step(limit: Float, enter: Float): Float {
        return if (limit > enter) 0.0f else 1.0f
    }

    /**
     * @param delta 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(delta: Double, min: Double, max: Double): Double {
        val mixFix = delta.coerceIn(0.0, 1.0)
        return min + (max - min) * mixFix
    }

    /**
     * @param delta 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(delta: Double, min: Float, max: Float): Float {
        val mixFix = delta.coerceIn(0.0, 1.0)
        return min + (max - min) * mixFix.toFloat()
    }


    @JvmStatic
    fun lerp(delta: Double, min: Quaternionf, max: Quaternionf): Quaternionf {
        return min.slerp(max, delta.toFloat(), Quaternionf())
    }

    @JvmStatic
    fun lerp(delta: Float, min: Quaternionf, max: Quaternionf): Quaternionf {
        return Quaternionf(min).slerp(max, delta)
    }


    /**
     * @param delta 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(delta: Float, min: Double, max: Double): Double {
        val mixFix = delta.coerceIn(0.0f, 1.0f)
        return min + (max - min) * mixFix
    }

    /**
     * @param delta 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(delta: Float, min: Float, max: Float): Float {
        val mixFix = delta.coerceIn(0.0f, 1.0f)
        return min + (max - min) * mixFix
    }

    @JvmStatic
    fun levelLerp(): LinerLevelLerp = LinerLevelLerp()

    /**
     * 区间范围内进行随机三角分布
     *
     * @param left 区间最小值
     * @param right 区间最大值
     * @param target 峰值目标
     * @return
     */
    @JvmStatic
    fun biasedRandomTriangle(
        left: Double,
        right: Double,
        target: Double
    ): Double {
        if (left == right) {
            return left
        }
        val u = Random.nextDouble()
        val c = (target - left) / (right - left)

        return if (u < c) {
            left + sqrt(u * (right - left) * (target - left))
        } else {
            right - sqrt((1 - u) * (right - left) * (right - target))
        }.coerceIn(left, right)
    }

    /**
     * 区间范围内进行随机正态分布
     *
     * @param left 区间最小值
     * @param right 区间最大值
     * @param target 峰值目标
     * @return
     */
    fun biasedRandomGaussian(
        left: Double,
        right: Double,
        target: Double
    ): Double {
        if (left == right) return left

        val min = minOf(left, right)
        val max = maxOf(left, right)
        val range = max - min
        val sigma = range / 6.0

        var v = target + rng.nextGaussian() * sigma

        if (v < min) v = min + (min - v)
        if (v > max) v = max - (v - max)

        return v.coerceIn(min, max)
    }

    fun progress(max: Number, current: Number): Double {
        val m = max.toDouble()
        val c = current.toDouble()
        return if (m > 0) (c / m).coerceIn(0.0, 1.0) else 0.0
    }

    /** 将数值限制在 [min, max] 范围内 */
    @JvmStatic
    fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)

    /** 将 v 从范围 [inMin, inMax] 线性映射到 [outMin, outMax]（不进行范围限制） */
    @JvmStatic
    fun remap(v: Double, inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double {
        if (inMax == inMin) return outMin
        val t = (v - inMin) / (inMax - inMin)
        return outMin + (outMax - outMin) * t
    }

    /** 反向线性插值：将范围 [a,b] 内的 v 转换为 [0,1] 范围内的 t（已限制范围） */
    @JvmStatic
    fun invLerpClamped(v: Double, a: Double, b: Double): Double {
        if (a == b) return 0.0
        return ((v - a) / (b - a)).coerceIn(0.0, 1.0)
    }

    /**
     * 计算每步的指数衰减因子。
     *
     * @param damping 阻尼强度（>=0）。值越大，衰减越快。
     * @param dt 步长时间。在 MC 中，你可以将每次 tick 视为 dt=1.0。
     * @return 用于乘以速度的因子，范围在 (0,1]。
     */
    @JvmStatic
    fun expDampFactor(damping: Double, dt: Double = 1.0): Double {
        if (damping <= 0.0) return 1.0
        // e^(-damping * dt)
        return exp(-damping * dt)
    }

    /**
     * 平滑距离衰减。
     *
     * @param distance 当前距离
     * @param start 效果开始衰减的距离（<= end）
     * @param end 效果降为 0 的距离
     * @param power >1 则在起点附近衰减更剧烈，<1 则更平缓
     * @return 范围在 [0,1] 内的衰减因子
     */
    @JvmStatic
    fun distanceFalloff(distance: Double, start: Double, end: Double, power: Double = 1.0): Double {
        if (end <= start) return if (distance <= start) 1.0 else 0.0
        val t = 1.0 - invLerpClamped(distance, start, end) // 靠近起点 => 1，靠近终点 => 0
        return t.pow(power.coerceAtLeast(1e-9))
    }

    /**
     * 反幂函数衰减：1 / (1 + (d/scale)^power)
     *
     * @param distance 距离，>=0
     * @param scale >0，控制有效范围（越大则衰减越慢）
     * @param power >=1，控制衰减锐度
     */
    @JvmStatic
    fun inversePowerFalloff(distance: Double, scale: Double, power: Double = 2.0): Double {
        val s = scale.coerceAtLeast(1e-9)
        val d = (distance.coerceAtLeast(0.0) / s)
        return 1.0 / (1.0 + d.pow(power.coerceAtLeast(1.0)))
    }

    /**
     * 将相位角包裹在 [0, 2π) 范围内。
     */
    @JvmStatic
    fun wrapRadians(rad: Double): Double {
        val twoPi = 2.0 * kotlin.math.PI
        var r = rad % twoPi
        if (r < 0) r += twoPi
        return r
    }

}

