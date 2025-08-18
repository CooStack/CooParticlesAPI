package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C.PacketType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class PacketCameraShakeS2C(val range: Double, val origin: Vec3, val amplitude: Double, val tick: Int) :
    CustomPacketPayload {
    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "camara_shake")
        val payloadID = CustomPacketPayload.Type<PacketCameraShakeS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketCameraShakeS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeDouble(packet.range)
                buf.writeVec3(packet.origin)
                buf.writeDouble(packet.amplitude)
                buf.writeInt(packet.tick)
            }, { buf ->
                val range = buf.readDouble()
                val origin = buf.readVec3()
                val amplitude = buf.readDouble()
                val tick = buf.readInt()
                PacketCameraShakeS2C(range, origin, amplitude, tick)
            })

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}