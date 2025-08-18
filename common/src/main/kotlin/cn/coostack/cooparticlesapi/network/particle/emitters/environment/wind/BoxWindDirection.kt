package cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind

import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.ezylang.evalex.Expression
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import kotlin.math.exp

class BoxWindDirection(
    override var direction: Vec3,
    var box: HitBox,
    var offset: RelativeLocation,
) : WindDirection {
    override var relative: Boolean = false
    override var windSpeedExpress: String = "1"
    override fun loadEmitters(emitters: ParticleEmitters): WindDirection {
        this.emitters = emitters
        return this
    }

    private var emitters: ParticleEmitters? = null

    companion object {
        @JvmStatic
        val CODEC = StreamCodec.of<FriendlyByteBuf, WindDirection>(
            { buf, data ->
                data as BoxWindDirection
                buf.writeVec3(data.direction)
                buf.writeBoolean(data.relative)
                buf.writeUtf(data.windSpeedExpress)
                buf.writeVec3(data.offset.toVector())
                buf.writeDouble(data.box.x1)
                buf.writeDouble(data.box.x2)
                buf.writeDouble(data.box.y1)
                buf.writeDouble(data.box.y2)
                buf.writeDouble(data.box.z1)
                buf.writeDouble(data.box.z2)
            }, {
                val direction = it.readVec3()
                val relative = it.readBoolean()
                val express = it.readUtf()
                val offset = RelativeLocation.of(it.readVec3())
                val box = HitBox(
                    it.readDouble(),
                    it.readDouble(),
                    it.readDouble(),
                    it.readDouble(),
                    it.readDouble(),
                    it.readDouble(),
                )
                BoxWindDirection(direction, box, offset).apply {
                    this.relative = relative
                    this.windSpeedExpress = express
                }
            }
        )
        const val ID = "box"
    }

    override fun getID(): String {
        return ID
    }

    override fun hasLoadedEmitters(): Boolean {
        return emitters != null
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
        val ofBox = box.ofBox(emitters!!.pos.add(offset.toVector()))
        return ofBox.contains(pos)
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, WindDirection> {
        return CODEC
    }
}