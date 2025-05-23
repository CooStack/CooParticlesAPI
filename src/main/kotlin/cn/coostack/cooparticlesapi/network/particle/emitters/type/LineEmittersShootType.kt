package cn.coostack.cooparticlesapi.network.particle.emitters.type

import cn.coostack.cooparticlesapi.barrages.HitBox
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d

class LineEmittersShootType(val dir: Vec3d, val step: Double) : EmittersShootType {
    companion object {
        @JvmStatic
        val CODEC: PacketCodec<RegistryByteBuf, EmittersShootType> =
            PacketCodec.ofStatic<RegistryByteBuf, EmittersShootType>(
                { buf, type ->
                    type as LineEmittersShootType
                    buf.writeVec3d(type.dir)
                    buf.writeDouble(type.step)

                }, {
                    val dir = it.readVec3d()
                    val step = it.readDouble()
                    LineEmittersShootType(dir, step)
                }
            )
        const val ID = "line"
    }

    override fun getID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<RegistryByteBuf, EmittersShootType> {
        return CODEC
    }

    override fun getPositions(
        origin: Vec3d,
        tick: Int,
        count: Int
    ): List<Vec3d> {
        return List(count) {
            origin.add(dir.normalize().multiply(it * step))
        }
    }

    override fun getDefaultDirection(
        enter: Vec3d,
        tick: Int,
        pos: Vec3d,
        origin: Vec3d
    ): Vec3d {
        return enter
    }
}