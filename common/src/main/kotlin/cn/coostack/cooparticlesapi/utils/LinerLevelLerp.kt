package cn.coostack.cooparticlesapi.utils

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * 多层插值
 */
class LinerLevelLerp {
    private var levels = mutableListOf<Double>()
    private var prevLevels = mutableListOf<Double>()
    fun addLevel(): LinerLevelLerp {
        levels.add(0.0)
        prevLevels.add(0.0)
        return this
    }

    fun setLevelProgress(level: Int, progress: Double): LinerLevelLerp {
        if (level >= levels.size) {
            throw ArrayIndexOutOfBoundsException("没有那么多的插值等级")
        }
        prevLevels[level] = levels[level]
        levels[level] = progress
        return this
    }

    fun setLevelProgress(level: Int, progress: Float): LinerLevelLerp {
        setLevelProgress(level, progress.toDouble())
        return this
    }

    fun lerp(min: Double, max: Double): Double {
        if (levels.isEmpty()) return min
        // 第一层level 
        var value = min + (max - min) * levels[0]
        // 下层插值
        for (i in 1 until levels.size) {
            val prev = min + (max - min) * prevLevels[i - 1]
            val now = min + (max - min) * levels[i - 1]
            value = prev + (now - prev) * levels[i]
        }

        return value
    }

    fun lerp(min: Float, max: Float): Float {
        return lerp(min.toDouble(), max.toDouble()).toFloat()
    }

    fun lerp(min: Vec3, max: Vec3): Vec3 {
        val minX = min.x
        val maxX = max.x
        val minY = max.y
        val maxY = max.y
        val minZ = max.z
        val maxZ = max.z
        return Vec3(
            lerp(minX, maxX),
            lerp(minY, maxY),
            lerp(minZ, maxZ),
        )
    }

    fun lerp(min: Vector3f, max: Vector3f): Vector3f {
        val minX = min.x
        val maxX = max.x
        val minY = max.y
        val maxY = max.y
        val minZ = max.z
        val maxZ = max.z
        return Vector3f(
            lerp(minX, maxX),
            lerp(minY, maxY),
            lerp(minZ, maxZ),
        )
    }
}