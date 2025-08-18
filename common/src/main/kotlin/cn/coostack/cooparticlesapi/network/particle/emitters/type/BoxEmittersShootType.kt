package cn.coostack.cooparticlesapi.network.particle.emitters.type

import cn.coostack.cooparticlesapi.barrages.HitBox
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

class BoxEmittersShootType(val box: HitBox) : EmittersShootType {
    companion object {
        @JvmStatic
        val CODEC: StreamCodec<FriendlyByteBuf, EmittersShootType> =
            StreamCodec.of<FriendlyByteBuf, EmittersShootType>(
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

    override fun getCodec(): StreamCodec<FriendlyByteBuf, EmittersShootType> {
        return CODEC
    }

    val random = Random(System.currentTimeMillis())
    override fun getPositions(origin: Vec3, tick: Int, count: Int): List<Vec3> {
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
            origin.add(Vec3(x, y, z))
        }
    }

    override fun getDefaultDirection(enter: Vec3, tick: Int, pos: Vec3, origin: Vec3): Vec3 {
        if (enter.length() < 1e-7) {
            // 随机速度
            val p = Vec3(
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0),
                random.nextDouble(-1.0, 1.0)
            )
            return p
        }
        return enter
    }
}