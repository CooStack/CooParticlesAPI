package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class PacketParticleStyleS2C(
    val uuid: UUID,
    val type: cn.coostack.cooparticlesapi.particles.control.ControlType,
    val args: Map<String, cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer<*>>
) {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_style")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_style")
        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeUUID(packet.uuid)
            buf.writeInt(packet.type.id)
            buf.writeInt(packet.args.size)
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
            val type = cn.coostack.cooparticlesapi.particles.control.ControlType.getTypeById(id)
            val argsCount = buf.readInt()
            repeat(argsCount) {
                val len = buf.readInt()
                val key = buf.readUtf()
                val byteBuf = buf.readBytes(len)
                val decode = cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers.decodeToBuffer<Any>(byteBuf)
                args[key] = decode
            }
            PacketParticleStyleS2C(
                uuid, type, args
            )
        })
    }
}
