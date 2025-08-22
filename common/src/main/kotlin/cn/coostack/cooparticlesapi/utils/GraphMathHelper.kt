package cn.coostack.cooparticlesapi.utils

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.pow

/**
 * 数学插值工具提供
 */
object GraphMathHelper {
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
    fun mix(c1: Vec3, c2: Vec3, mix: Double): Vec3 {
        return Vec3(mix(c1.toVector3f(), c2.toVector3f(), mix))
    }

    @JvmStatic
    fun mix(c1: Vector3f, c2: Vector3f, mix: Double): Vector3f {
        val x = lerp(mix, c1.x, c2.x)
        val y = lerp(mix, c1.y, c2.y)
        val z = lerp(mix, c1.z, c2.z)
        return Vector3f(x, y, z)
    }


    @JvmStatic
    fun lerp(mix: Vec3, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(mix.x, 0.0, stepX)
        val mixY = lerp(mix.y, 0.0, stepY)
        val mixZ = lerp(mix.z, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(mix: Float, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(mix, 0.0, stepX)
        val mixY = lerp(mix, 0.0, stepY)
        val mixZ = lerp(mix, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(mix: Float, min: Vector3f, max: Vector3f): Vector3f {
        return lerp(mix, Vec3(min), Vec3(max)).toVector3f()
    }

    @JvmStatic
    fun lerp(mix: Double, min: Vector3f, max: Vector3f): Vector3f {
        return lerp(mix, Vec3(min), Vec3(max)).toVector3f()
    }

    @JvmStatic
    fun lerp(mix: Double, min: Vec3, max: Vec3): Vec3 {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(mix, 0.0, stepX)
        val mixY = lerp(mix, 0.0, stepY)
        val mixZ = lerp(mix, 0.0, stepZ)
        return min.add(Vec3(mixX, mixY, mixZ))
    }

    @JvmStatic
    fun lerp(mix: Vector3f, min: Vector3f, max: Vector3f): Vector3f {
        val stepX = max.x - min.x
        val stepY = max.y - min.y
        val stepZ = max.z - min.z
        val mixX = lerp(mix.x, 0f, stepX)
        val mixY = lerp(mix.y, 0f, stepY)
        val mixZ = lerp(mix.z, 0f, stepZ)
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
     * @param mix 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(mix: Double, min: Double, max: Double): Double {
        val mixFix = mix.coerceIn(0.0, 1.0)
        return min + (max - min) * mixFix
    }

    /**
     * @param mix 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(mix: Double, min: Float, max: Float): Float {
        val mixFix = mix.coerceIn(0.0, 1.0)
        return min + (max - min) * mixFix.toFloat()
    }

    /**
     * @param mix 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(mix: Float, min: Double, max: Double): Double {
        val mixFix = mix.coerceIn(0.0f, 1.0f)
        return min + (max - min) * mixFix
    }

    /**
     * @param mix 输入一个0..1的值 插值从 min 到 max之间的数值
     */
    @JvmStatic
    fun lerp(mix: Float, min: Float, max: Float): Float {
        val mixFix = mix.coerceIn(0.0f, 1.0f)
        return min + (max - min) * mixFix
    }
}

