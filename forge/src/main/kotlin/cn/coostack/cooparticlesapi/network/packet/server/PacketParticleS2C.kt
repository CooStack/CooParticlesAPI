package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.codec.ForgeCodecHelper
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class PacketParticleS2C(
    val type: ParticleOptions,
    val pos: Vec3,
    val velocity: Vec3,
) {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle")
        val payloadID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle")

        val CODEC = ForgeStreamCodec.of({ packet, buf ->
            buf.writeVec3(packet.pos)
            buf.writeVec3(packet.velocity)
            ForgeCodecHelper.particleCodecOf(packet.type).encode(buf, packet.type)
        }, { buf ->
            val pos = buf.readVec3()
            val velocity = buf.readVec3()
            val type = ForgeCodecHelper.particleCodecOf(ParticleOptions {}.writeToPacket(PacketByteBuf(net.minecraftforge.network.NetworkEvent.INSTANCE.get()?.get() ?: net.io.netty.buffer.Unpooled.buffer())).let { 
                net.minecraft.core.particles.ParticleTypes.END_ROD
            }).decode(buf)
            PacketParticleS2C(type, pos, velocity)
        })
    }
}
