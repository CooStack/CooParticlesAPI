package cn.coostack.cooparticlesapi.network.particle.emitters.type

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

class PointEmittersShootType : EmittersShootType {
    val random = Random(System.currentTimeMillis())
    companion object {
        @JvmStatic
        val CODEC: PacketCodec<PacketByteBuf, EmittersShootType> =
            PacketCodec.ofStatic<PacketByteBuf, EmittersShootType>(
                { b, p -> }, {
                    PointEmittersShootType()
                }
            )
        const val ID = "point"
    }

    override fun getID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, EmittersShootType> {
        return CODEC
    }

    override fun getPositions(origin: Vec3d, tick: Int, count: Int): List<Vec3d> {
        return List(count) {
            origin
        }
    }

    override fun getDefaultDirection(enter: Vec3d, tick: Int, pos: Vec3d, origin: Vec3d): Vec3d {
        if (enter.length() < 1e-7) {
            // 随机速度
            val p = Vec3d(
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0)
            )
            return p
        }
        return enter
    }
}