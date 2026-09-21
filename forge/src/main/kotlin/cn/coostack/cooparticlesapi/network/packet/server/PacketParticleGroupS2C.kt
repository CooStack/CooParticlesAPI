package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleGroupS2C(
    val uuid: UUID,
    val type: cn.coostack.cooparticlesapi.particles.control.ControlType,
    val args: Map<String, cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer<*>>
) {
    enum class PacketArgsType(val ofArgs: String) {
        POS("pos"),
        CURRENT_TICK("current_tick"),
        MAX_TICK("max_tick"),
        ROTATE_TO("rotate_to"),
        ROTATE_AXIS("rotate_axis"),
        INVOKE("invoke"),
        AXIS("axis"),
        SCALE("scale"),
        GROUP_TYPE("groupType");

        companion object {
            fun fromArgsName(value: String): PacketArgsType {
                return when (value) {
                    "pos" -> POS
                    "current_tick" -> CURRENT_TICK
                    "max_tick" -> MAX_TICK
                    "rotate_to" -> ROTATE_TO
                    "rotate_axis" -> ROTATE_AXIS
                    "invoke" -> INVOKE
                    "scale" -> SCALE
                    "groupType" -> GROUP_TYPE
                    else -> INVOKE
                }
            }
        }
    }

    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_group")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_group")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUUID(packet.uuid)
            buf.writeInt(packet.type.id)
            packet.args.forEach { (t, u) ->
                val encode = cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers.encode(u)
                val len = encode.size
                buf.writeInt(len)
                buf.writeUtf(t)
                buf.writeBytes(encode)
            }
        }, { buf ->
            val args = HashMap<String, cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer<*>>()
            val uuid = buf.readUUID()
            val id = buf.readInt()
            val type = cn.coostack.cooparticlesapi.particles.control.ControlType.Companion.getTypeById(id)
            while (buf.readableBytes() != 0) {
                val len = buf.readInt()
                val key = buf.readUtf()
                val value = ByteArray(len)
                buf.readBytes(value)
                val decode = cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers.decodeToBuffer<Any>(value)
                args[key] = decode
            }
            PacketParticleGroupS2C(
                uuid, type, args
            )
        })
    }
}
