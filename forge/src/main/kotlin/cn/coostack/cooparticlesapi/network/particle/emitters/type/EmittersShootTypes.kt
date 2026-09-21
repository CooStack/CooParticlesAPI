package cn.coostack.cooparticlesapi.network.particle.emitters.type

import net.minecraft.network.PacketByteBuf

object EmittersShootTypes {
    val CODEC: ForgeStreamCodec<cn.coostack.cooparticlesapi.network.particle.emitters.type.EmittersShootType> =
        cn.coostack.cooparticlesapi.annotations.codec.ForgeStreamCodec.of(
            { buf, type ->
                when (type) {
                    is BoxEmittersShootType -> buf.writeByte(0)
                    is LineEmittersShootType -> buf.writeByte(1)
                    is PointEmittersShootType -> buf.writeByte(2)
                    is MathEmittersShootType -> buf.writeByte(3)
                    else -> buf.writeByte(-1)
                }
            },
            { buf ->
                when (buf.readUnsignedByte().toInt()) {
                    0 -> BoxEmittersShootType
                    1 -> LineEmittersShootType
                    2 -> PointEmittersShootType
                    3 -> MathEmittersShootType
                    else -> throw IllegalArgumentException("Unknown shoot type")
                }
            }
        )
}
