package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

object WindDirections {
    private val packets = HashMap<String, StreamCodec<FriendlyByteBuf, WindDirection>>()

    val GLOBAL = register("global", GlobalWindDirection.CODEC)

    val BOX = register("box", BoxWindDirection.CODEC)

    val BALL = register("ball", BallWindDirection.CODEC)

    /**
     * 物理处理风力
     * @param v 先前移动方向
     * @param pos 在风场中的位置 (在粒子发射器中就是粒子位置) 如果超出范围则会返回0向量
     */
    fun handleWindForce(
        wind: WindDirection,
        pos: Vec3,
        airDensity: Double,
        dragCoefficient: Double,
        crossSectionalArea: Double,
        v: Vec3 = Vec3.ZERO,
    ): Vec3 {
        if (!wind.inRange(pos)) {
            return Vec3.ZERO
        }
        val windVec = wind.getWind(pos)
        return if (windVec.lengthSqr() > 0) {
            val relativeWind = windVec.subtract(v)
            val windMagnitude = 0.5 * airDensity * dragCoefficient *
                    crossSectionalArea * relativeWind.lengthSqr() * 0.05
            relativeWind.normalize().scale(windMagnitude)
        } else {
            Vec3.ZERO
        }
    }

    fun getCodecFromID(id: String): StreamCodec<FriendlyByteBuf, WindDirection> {
        return packets[id]!!
    }

    fun register(
        id: String,
        codec: StreamCodec<FriendlyByteBuf, WindDirection>
    ): StreamCodec<FriendlyByteBuf, WindDirection> {
        packets[id] = codec
        return codec
    }

    fun init() {}
}