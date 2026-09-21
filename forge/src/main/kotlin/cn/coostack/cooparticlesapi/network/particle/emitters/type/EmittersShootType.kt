package cn.coostack.cooparticlesapi.network.particle.emitters.type

import net.minecraft.network.PacketByteBuf

interface EmittersShootType {
    fun nextShoot(): Pair<Double, Double>
}

object BoxEmittersShootType : EmittersShootType {
    override fun nextShoot(): Pair<Double, Double> {
        TODO("Not yet implemented")
    }
}

object LineEmittersShootType : EmittersShootType {
    override fun nextShoot(): Pair<Double, Double> {
        TODO("Not yet implemented")
    }
}

object PointEmittersShootType : EmittersShootType {
    override fun nextShoot(): Pair<Double, Double> {
        TODO("Not yet implemented")
    }
}

object MathEmittersShootType : EmittersShootType {
    override fun nextShoot(): Pair<Double, Double> {
        TODO("Not yet implemented")
    }
}

object EmittersShootTypes {
    val CODEC: ForgeStreamCodec<EmittersShootType> = ForgeStreamCodec.of(
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
