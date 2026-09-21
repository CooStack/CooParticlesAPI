package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.network.packet.api.CooPacketEnvelopeS2C
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

class ForgeServerNetworking : ServerNetworking {
    override fun send(packet: CooPacket, to: ServerPlayer) {
        val data = CooPacketRegistry.encode(packet)
        val envelope = CooPacketEnvelopeS2C(
            kindId = 0,
            packetId = packet.id(),
            correlationId = 0,
            timeoutTicks = 0,
            data = data,
        )
        ForgeNetworkChannel.INSTANCE.sendEnvelopeS2CTo(envelope, to)
    }

    override fun sendAllPlayers(packet: CooPacket) {
        val data = CooPacketRegistry.encode(packet)
        val envelope = CooPacketEnvelopeS2C(
            kindId = 0,
            packetId = packet.id(),
            correlationId = 0,
            timeoutTicks = 0,
            data = data,
        )
        ForgeNetworkChannel.INSTANCE.sendEnvelopeS2CToAll(envelope)
    }

    override fun sendToPlayersTrackingChunk(world: ServerLevel, chunk: ChunkPos, packet: CooPacket) {
        val data = CooPacketRegistry.encode(packet)
        val envelope = CooPacketEnvelopeS2C(
            kindId = 0,
            packetId = packet.id(),
            correlationId = 0,
            timeoutTicks = 0,
            data = data,
        )
        ForgeNetworkChannel.INSTANCE.sendEnvelopeS2CToTrackingChunk(envelope, world, chunk)
    }
}
