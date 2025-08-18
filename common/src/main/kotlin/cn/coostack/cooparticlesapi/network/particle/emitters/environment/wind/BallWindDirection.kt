package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.ezylang.evalex.Expression
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3

class BallWindDirection(
    override var direction: Vec3,
    var radius: Double,
    var offset: RelativeLocation,
) : WindDirection {
    override var relative: Boolean = false
    override var windSpeedExpress: String = "1"
    override fun loadEmitters(emitters: ParticleEmitters): WindDirection {
        this.emitters = emitters
        return this
    }

    override fun hasLoadedEmitters(): Boolean {
        return emitters != null
    }

    private var emitters: ParticleEmitters? = null

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, WindDirection>(
            { buf, data ->
                data as BallWindDirection
                buf.writeVec3(data.direction)
                buf.writeBoolean(data.relative)
                buf.writeUtf(data.windSpeedExpress)
                buf.writeVec3(data.offset.toVector())
                buf.writeDouble(data.radius)
            }, {
                val direction = it.readVec3()
                val relative = it.readBoolean()
                val express = it.readUtf()
                val offset = RelativeLocation.of(it.readVec3())
                val radius = it.readDouble()
                BallWindDirection(direction, radius, offset).apply {
                    this.relative = relative
                    this.windSpeedExpress = express
                }
            }
        )
        const val ID = "ball"
    }

    override fun getID(): String {
        return ID
    }

    override fun getWind(particlePos: Vec3): Vec3 {
        if (relative) {
            val pos = emitters?.pos ?: return direction
            val dir = particlePos.subtract(pos)
            val express = Expression(windSpeedExpress)
                .with("l", dir.length())
                .evaluate().numberValue.toDouble()
            dir.normalize().scale(express)
            return dir
        }
        return direction
    }

    override fun inRange(pos: Vec3): Boolean {
        return emitters!!.pos.add(offset.toVector()).subtract(pos).length() <= radius
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, WindDirection> {
        return CODEC
    }
}