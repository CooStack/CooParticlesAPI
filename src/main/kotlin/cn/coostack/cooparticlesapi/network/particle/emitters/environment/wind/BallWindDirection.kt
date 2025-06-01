package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.ezylang.evalex.Expression
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import kotlin.math.exp

class BallWindDirection(
    override var direction: Vec3d,
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
        val CODEC = PacketCodec.ofStatic<PacketByteBuf, WindDirection>(
            { buf, data ->
                data as BallWindDirection
                buf.writeVec3d(data.direction)
                buf.writeBoolean(data.relative)
                buf.writeString(data.windSpeedExpress)
                buf.writeVec3d(data.offset.toVector())
                buf.writeDouble(data.radius)
            }, {
                val direction = it.readVec3d()
                val relative = it.readBoolean()
                val express = it.readString()
                val offset = RelativeLocation.of(it.readVec3d())
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

    override fun getWind(particlePos: Vec3d): Vec3d {
        if (relative) {
            val pos = emitters?.pos ?: return direction
            val dir = pos.relativize(particlePos)
            val express = Expression(windSpeedExpress)
                .with("l", dir.length())
                .evaluate().numberValue.toDouble()
            dir.normalize().multiply(express)
            return dir
        }
        return direction
    }

    override fun inRange(pos: Vec3d): Boolean {
        return pos.relativize(emitters!!.pos.add(offset.toVector())).length() <= radius
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, WindDirection> {
        return CODEC
    }
}