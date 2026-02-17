package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/**
 *
 */
class PacketParticleS2C(
    val type: ParticleOptions,
    val pos: Vec3,
    val velocity: Vec3,
) : CustomPacketPayload {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle")
        val payloadID = CustomPacketPayload.Type<PacketParticleS2C>(identifierID)
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PacketParticleS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeVec3(packet.pos)
                buf.writeVec3(packet.velocity)
                ParticleTypes.STREAM_CODEC.encode(buf, packet.type)
            }, { buf ->
                val pos = buf.readVec3()
                val velocity = buf.readVec3()
                val type = ParticleTypes.STREAM_CODEC.decode(buf)
                return@codec PacketParticleS2C(type, pos, velocity)
            })

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}