package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.extend.random
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
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
                writeDouble(it.maxSpeed)
                writeDouble(it.minSpeed)
            }
        }, {
            SimpleRandomParticleData().apply {
                maxAge = it.readInt()
                minAge = it.readInt()
                maxCount = it.readInt()
                minCount = it.readInt()
                maxSize = it.readDouble()
                minSize = it.readDouble()
                maxSpeed = it.readDouble()
                minSpeed = it.readDouble()
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
    var maxSize = 0.3
    var minSize = 0.1

    /**
     * 解决你写粒子随机速度的参数
     */
    var minSpeed = 0.1
    var maxSpeed = 1.0


    fun getRandomParticleMaxAge(): Int = if (maxAge > minAge) {
        Random.nextInt(minAge, maxAge)
    } else minAge
    fun getRandomCount(): Int = if (maxCount > minCount) Random.nextInt(minCount, maxCount) else minCount
    fun getRandomSize(): Float =
        if (maxSize > minSize) Random.nextDouble(minSize, maxSize).toFloat() else minSize.toFloat()

    fun getRandomSpeed(): Double = if (maxSpeed > minSpeed) Random.nextDouble(minSpeed, maxSpeed) else minSpeed

}