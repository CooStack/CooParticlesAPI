package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class PacketParticleBatchS2C(
    val type: ParticleOptions,
    val positions: List<Vec3>,
    val velocity: Vec3,
) : CustomPacketPayload {
    init {
        require(positions.size <= MAX_PARTICLES) { "particle batch exceeds $MAX_PARTICLES entries" }
    }

    companion object {
        const val MAX_PARTICLES = 4096

        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_batch")
        val payloadID = CustomPacketPayload.Type<PacketParticleBatchS2C>(identifierID)
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PacketParticleBatchS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                ParticleTypes.STREAM_CODEC.encode(buf, packet.type)
                buf.writeVec3(packet.velocity)
                buf.writeVarInt(packet.positions.size)
                packet.positions.forEach { buf.writeVec3(it) }
            }, { buf ->
                val type = ParticleTypes.STREAM_CODEC.decode(buf)
                val velocity = buf.readVec3()
                val size = buf.readVarInt()
                require(size in 0..MAX_PARTICLES) { "invalid particle batch size: $size" }
                val positions = List(size) { buf.readVec3() }
                PacketParticleBatchS2C(type, positions, velocity)
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = payloadID
}
