package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import net.minecraft.network.PacketByteBuf

object WindDirections {
    val CODEC: ForgeStreamCodec<WindDirection> = ForgeStreamCodec.of(
        { buf, dir ->
            when (dir) {
                is BallWindDirection -> buf.writeByte(0)
                is BoxWindDirection -> buf.writeByte(1)
                is GlobalWindDirection -> buf.writeByte(2)
                else -> buf.writeByte(-1)
            }
        },
        { buf ->
            when (buf.readUnsignedByte().toInt()) {
                0 -> BallWindDirection
                1 -> BoxWindDirection
                2 -> GlobalWindDirection
                else -> throw IllegalArgumentException("Unknown wind direction type")
            }
        }
    )
}
