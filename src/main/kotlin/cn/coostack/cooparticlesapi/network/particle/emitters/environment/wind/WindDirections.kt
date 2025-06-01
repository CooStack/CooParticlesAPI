package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d

object WindDirections {
    private val packets = HashMap<String, PacketCodec<PacketByteBuf, WindDirection>>()

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
        pos: Vec3d,
        airDensity: Double,
        dragCoefficient: Double,
        crossSectionalArea: Double,
        v: Vec3d = Vec3d.ZERO,
    ): Vec3d {
        if (!wind.inRange(pos)) {
            return Vec3d.ZERO
        }
        val windVec = wind.getWind(pos)
        return if (windVec.lengthSquared() > 0) {
            val relativeWind = windVec.subtract(v)
            val windMagnitude = 0.5 * airDensity * dragCoefficient *
                    crossSectionalArea * relativeWind.lengthSquared() * 0.05
            relativeWind.normalize().multiply(windMagnitude)
        } else {
            Vec3d.ZERO
        }
    }

    fun getCodecFromID(id: String): PacketCodec<PacketByteBuf, WindDirection> {
        return packets[id]!!
    }

    fun register(
        id: String,
        codec: PacketCodec<PacketByteBuf, WindDirection>
    ): PacketCodec<PacketByteBuf, WindDirection> {
        packets[id] = codec
        return codec
    }

    fun init() {}
}