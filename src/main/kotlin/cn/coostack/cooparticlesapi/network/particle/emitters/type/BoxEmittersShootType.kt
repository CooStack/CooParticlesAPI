package cn.coostack.cooparticlesapi.network.particle.emitters.type

import cn.coostack.cooparticlesapi.barrages.HitBox
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

class BoxEmittersShootType(val box: HitBox) : EmittersShootType {
    companion object {
        @JvmStatic
        val CODEC: PacketCodec<PacketByteBuf, EmittersShootType> =
            PacketCodec.ofStatic<PacketByteBuf, EmittersShootType>(
                { buf, type ->
                    type as BoxEmittersShootType
                    val box = type.box
                    buf.writeDouble(box.x1)
                    buf.writeDouble(box.y1)
                    buf.writeDouble(box.z1)
                    buf.writeDouble(box.x2)
                    buf.writeDouble(box.y2)
                    buf.writeDouble(box.z2)

                }, {
                    val box = HitBox(
                        it.readDouble(),
                        it.readDouble(),
                        it.readDouble(),
                        it.readDouble(),
                        it.readDouble(),
                        it.readDouble(),
                    )
                    BoxEmittersShootType(box)
                }
            )
        const val ID = "box"
    }

    override fun getID(): String {
        return ID
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, EmittersShootType> {
        return CODEC
    }

    val random = Random(System.currentTimeMillis())
    override fun getPositions(origin: Vec3d, tick: Int, count: Int): List<Vec3d> {
        return List(count) {
            val x = random.nextDouble(
                box.x1,
                box.x2
            )
            val y = random.nextDouble(
                box.y1,
                box.y2
            )
            val z = random.nextDouble(
                box.z1,
                box.z2
            )
            origin.add(Vec3d(x, y, z))
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