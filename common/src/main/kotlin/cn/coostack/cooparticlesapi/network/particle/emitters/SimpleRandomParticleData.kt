package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 防止老是要手写等 一堆莫名其妙的随机数据而设立的
 * @param maxAge 粒子最大生命周期
 * @param minAge 粒子最小生命周期
 * @param maxCount 粒子最大个数
 * @param minCount 粒子最小个数
 * @param maxSize 粒子最大尺寸
 * @param minSize 粒子最小尺寸
 * @param maxSpeed 粒子最大速度
 * @param minSpeed 粒子最小速度
 *
 */
class SimpleRandomParticleData {

    companion object {
        val PACKET_CODEC = StreamCodec.of<FriendlyByteBuf, SimpleRandomParticleData>({ buf, it ->
            buf.apply {
                writeInt(it.maxAge)
                writeInt(it.minAge)
                writeInt(it.maxCount)
                writeInt(it.minCount)
                writeDouble(it.maxSize)
                writeDouble(it.minSize)
                writeDouble(it.maxSizeX)
                writeDouble(it.minSizeX)
                writeDouble(it.maxSizeY)
                writeDouble(it.minSizeY)
                writeDouble(it.maxSizeZ)
                writeDouble(it.minSizeZ)
                writeDouble(it.maxSpeed)
                writeDouble(it.minSpeed)
                writeFloat(it.maxYaw)
                writeFloat(it.minYaw)
                writeFloat(it.maxPitch)
                writeFloat(it.minPitch)
                writeFloat(it.maxRoll)
                writeFloat(it.minRoll)
                writeFloat(it.maxAlpha)
                writeFloat(it.minAlpha)
                writeVector3f(it.leftColor)
                writeVector3f(it.rightColor)
            }
        }, {
            SimpleRandomParticleData().apply {
                maxAge = it.readInt()
                minAge = it.readInt()
                maxCount = it.readInt()
                minCount = it.readInt()
                maxSize = it.readDouble()
                minSize = it.readDouble()
                maxSizeX = it.readDouble()
                minSizeX = it.readDouble()
                maxSizeY = it.readDouble()
                minSizeY = it.readDouble()
                maxSizeZ = it.readDouble()
                minSizeZ = it.readDouble()
                maxSpeed = it.readDouble()
                minSpeed = it.readDouble()
                maxYaw = it.readFloat()
                minYaw = it.readFloat()
                maxPitch = it.readFloat()
                minPitch = it.readFloat()
                maxRoll = it.readFloat()
                minRoll = it.readFloat()
                maxAlpha = it.readFloat()
                minAlpha = it.readFloat()
                leftColor = it.readVector3f()
                rightColor = it.readVector3f()
            }
        })
    }

    /**
     * 解决你写粒子随机生命周期的参数
     */
    var maxAge = 10
    var minAge = 1

    /**
     * 解决你写粒子随机个数的参数
     */
    var maxCount = 10
    var minCount = 1

    /**
     * 解决你写粒子随机大小的参数
     */
    private var currentMaxSize = 0.3
    var maxSize: Double
        get() = currentMaxSize
        set(value) {
            currentMaxSize = value
            maxSizeX = value
            maxSizeY = value
            maxSizeZ = value
        }

    private var currentMinSize = 0.1
    var minSize: Double
        get() = currentMinSize
        set(value) {
            currentMinSize = value
            minSizeX = value
            minSizeY = value
            minSizeZ = value
        }

    var maxSizeX = currentMaxSize
    var minSizeX = currentMinSize
    var maxSizeY = currentMaxSize
    var minSizeY = currentMinSize
    var maxSizeZ = currentMaxSize
    var minSizeZ = currentMinSize

    /**
     * 解决你写粒子随机速度的参数
     */
    var minSpeed = 0.1
    var maxSpeed = 1.0

    var maxYaw = 0f
    var minYaw = 0f
    var maxPitch = 0f
    var minPitch = 0f
    var maxRoll = 0f
    var minRoll = 0f

    var minAlpha = 1f
    var maxAlpha = 1f

    var leftColor = Vector3f(1f, 1f, 1f)
    var rightColor = Vector3f(1f, 1f, 1f)


    fun getRandomParticleMaxAge(): Int = if (maxAge > minAge) {
        Random.nextInt(minAge, maxAge)
    } else minAge
    fun getRandomCount(): Int = if (maxCount > minCount) Random.nextInt(minCount, maxCount) else minCount
    fun getRandomSize(): Float =
        if (maxSize > minSize) Random.nextDouble(minSize, maxSize).toFloat() else minSize.toFloat()

    fun getRandomSpeed(): Double = if (maxSpeed > minSpeed) Random.nextDouble(minSpeed, maxSpeed) else minSpeed

    fun getRandomSizeX(): Float = getRandomDouble(minSizeX, maxSizeX).toFloat()
    fun getRandomSizeY(): Float = getRandomDouble(minSizeY, maxSizeY).toFloat()
    fun getRandomSizeZ(): Float = getRandomDouble(minSizeZ, maxSizeZ).toFloat()
    fun getRandomYaw(): Float = getRandomFloat(minYaw, maxYaw)
    fun getRandomPitch(): Float = getRandomFloat(minPitch, maxPitch)
    fun getRandomRoll(): Float = getRandomFloat(minRoll, maxRoll)
    fun getRandomAlpha(): Float = getRandomFloat(minAlpha, maxAlpha)
    fun getRandomColor(): Vector3f {
        return Vector3f(
            getRandomBetween(leftColor.x, rightColor.x),
            getRandomBetween(leftColor.y, rightColor.y),
            getRandomBetween(leftColor.z, rightColor.z),
        )
    }

    fun getInterpolatedParticleMaxAge(progress: Number): Int = getInterpolatedInt(progress, minAge, maxAge)
    fun getInterpolatedCount(progress: Number): Int = getInterpolatedInt(progress, minCount, maxCount)
    fun getInterpolatedSize(progress: Number): Float = getInterpolatedDouble(progress, minSize, maxSize).toFloat()
    fun getInterpolatedSizeX(progress: Number): Float = getInterpolatedDouble(progress, minSizeX, maxSizeX).toFloat()
    fun getInterpolatedSizeY(progress: Number): Float = getInterpolatedDouble(progress, minSizeY, maxSizeY).toFloat()
    fun getInterpolatedSizeZ(progress: Number): Float = getInterpolatedDouble(progress, minSizeZ, maxSizeZ).toFloat()
    fun getInterpolatedSpeed(progress: Number): Double = getInterpolatedDouble(progress, minSpeed, maxSpeed)
    fun getInterpolatedYaw(progress: Number): Float = getInterpolatedFloat(progress, minYaw, maxYaw)
    fun getInterpolatedPitch(progress: Number): Float = getInterpolatedFloat(progress, minPitch, maxPitch)
    fun getInterpolatedRoll(progress: Number): Float = getInterpolatedFloat(progress, minRoll, maxRoll)
    fun getInterpolatedAlpha(progress: Number): Float = getInterpolatedFloat(progress, minAlpha, maxAlpha)
    fun getInterpolatedColor(progress: Number): Vector3f {
        return GraphMathHelper.lerp(progress.toDouble().coerceIn(0.0, 1.0), leftColor, rightColor)
    }

    fun interpolate(progress: Number): ControlableParticleData {
        return setupInterpolated(progress, ControlableParticleData())
    }

    fun setupInterpolated(progress: Number, data: ControlableParticleData): ControlableParticleData {
        data.maxAge = getInterpolatedParticleMaxAge(progress)
        data.speed = getInterpolatedSpeed(progress)
        setupSize(
            data,
            getInterpolatedSizeX(progress),
            getInterpolatedSizeY(progress),
            getInterpolatedSizeZ(progress)
        )
        data.yaw = getInterpolatedYaw(progress)
        data.pitch = getInterpolatedPitch(progress)
        data.roll = getInterpolatedRoll(progress)
        data.alpha = getInterpolatedAlpha(progress)
        data.color = getInterpolatedColor(progress)
        return data
    }

    fun setupRandomly(data: ControlableParticleData): ControlableParticleData {
        val (sizeX, sizeY, sizeZ) = if (hasUniformSizeRange()) {
            val size = getRandomSizeX()
            Triple(size, size, size)
        } else {
            Triple(getRandomSizeX(), getRandomSizeY(), getRandomSizeZ())
        }

        data.maxAge = getRandomParticleMaxAge()
        data.speed = getRandomSpeed()
        setupSize(data, sizeX, sizeY, sizeZ)
        data.yaw = getRandomYaw()
        data.pitch = getRandomPitch()
        data.roll = getRandomRoll()
        data.alpha = getRandomAlpha()
        data.color = getRandomColor()
        return data
    }

    private fun setupSize(data: ControlableParticleData, sizeX: Float, sizeY: Float, sizeZ: Float) {
        data.uniformSize = sizeX == sizeY
        data.weightSize = sizeX
        data.heightSize = sizeY
        data.depthSize = sizeZ
    }

    private fun hasUniformSizeRange(): Boolean {
        return minSizeX == minSizeY && minSizeY == minSizeZ &&
                maxSizeX == maxSizeY && maxSizeY == maxSizeZ
    }

    private fun getRandomDouble(min: Double, max: Double): Double {
        return if (max > min) Random.nextDouble(min, max) else min
    }

    private fun getRandomFloat(min: Float, max: Float): Float {
        return if (max > min) Random.nextDouble(min.toDouble(), max.toDouble()).toFloat() else min
    }

    private fun getRandomBetween(left: Float, right: Float): Float {
        val min = minOf(left, right)
        val max = maxOf(left, right)
        return if (max > min) Random.nextDouble(min.toDouble(), max.toDouble()).toFloat() else left
    }

    private fun getInterpolatedInt(progress: Number, min: Int, max: Int): Int {
        return GraphMathHelper.lerp(progress.toDouble(), min.toDouble(), max.toDouble()).roundToInt()
    }

    private fun getInterpolatedDouble(progress: Number, min: Double, max: Double): Double {
        return GraphMathHelper.lerp(progress.toDouble(), min, max)
    }

    private fun getInterpolatedFloat(progress: Number, min: Float, max: Float): Float {
        return GraphMathHelper.lerp(progress.toDouble(), min, max)
    }

}
