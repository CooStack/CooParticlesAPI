package cn.coostack.cooparticlesapi.network.packet

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C.PacketType
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

class PacketCameraShakeS2C(val range: Double, val origin: Vec3d, val amplitude: Double, val tick: Int) : CustomPayload {
    companion object {
        private val identifierID = Identifier.of(CooParticleAPI.MOD_ID, "camara_shake")
        val payloadID = CustomPayload.Id<PacketCameraShakeS2C>(identifierID)
        private val CODEC: PacketCodec<PacketByteBuf, PacketCameraShakeS2C> =
            CustomPayload.codecOf({ packet, buf ->
                buf.writeDouble(packet.range)
                buf.writeVec3d(packet.origin)
                buf.writeDouble(packet.amplitude)
                buf.writeInt(packet.tick)
            }, { buf ->
                val range = buf.readDouble()
                val origin = buf.readVec3d()
                val amplitude = buf.readDouble()
                val tick = buf.readInt()
                PacketCameraShakeS2C(range, origin, amplitude, tick)
            })

        fun init() {
            PayloadTypeRegistry.playS2C().register(payloadID, CODEC)
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> {
        return payloadID
    }
}