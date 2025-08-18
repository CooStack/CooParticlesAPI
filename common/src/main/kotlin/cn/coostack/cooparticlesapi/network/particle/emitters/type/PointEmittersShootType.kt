package cn.coostack.cooparticlesapi.network.particle.emitters.type

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

class PointEmittersShootType : EmittersShootType {
    val random = Random(System.currentTimeMillis())

    companion object {
        @JvmStatic
        val CODEC: StreamCodec<FriendlyByteBuf, EmittersShootType> =
            StreamCodec.of<FriendlyByteBuf, EmittersShootType>(
                { b, p -> }, {
                    PointEmittersShootType()
                }
            )
        const val ID = "point"
    }

    override fun getID(): String {
        return ID
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, EmittersShootType> {
        return CODEC
    }

    override fun getPositions(origin: Vec3, tick: Int, count: Int): List<Vec3> {
        return List(count) {
            origin
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