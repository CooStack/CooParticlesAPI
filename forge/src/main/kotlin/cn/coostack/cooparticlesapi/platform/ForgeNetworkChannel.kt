package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeC2S
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeS2C
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel

object ForgeNetworkChannel {
    val channel: SimpleChannel = net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
        ResourceLocation(CooParticlesConstants.MOD_ID, "main"),
        { true },
        { true },
        { true }
    )
    private var nextId = 0

    fun registerEnvelopeS2C(handler: (CooPacketEnvelopeS2C) -> Unit = {}) {
        channel.registerMessage(nextId++, CooPacketEnvelopeS2C::class.java,
            { packet, buf -> CooPacketEnvelopeS2C.write(buf, packet) },
            { buf -> CooPacketEnvelopeS2C.read(buf) },
            { packet, ctx ->
                handler(packet)
                ctx.packetHandled = true
            }
        )
    }

    fun registerEnvelopeC2S(handler: (CooPacketEnvelopeC2S, ServerPlayer) -> Unit = { _, _ -> }) {
        channel.registerMessage(nextId++, CooPacketEnvelopeC2S::class.java,
            { packet, buf -> CooPacketEnvelopeC2S.write(buf, packet) },
            { buf -> CooPacketEnvelopeC2S.read(buf) },
            { packet, ctx ->
                if (ctx.sender != null) {
                    handler(packet, ctx.sender)
                }
                ctx.packetHandled = true
            }
        )
    }

    fun sendEnvelopeS2CTo(envelope: CooPacketEnvelopeS2C, player: ServerPlayer) {
        channel.sendTo(player, envelope)
    }

    fun sendEnvelopeS2CToAll(envelope: CooPacketEnvelopeS2C) {
        channel.sendToAll(envelope)
    }

    fun sendEnvelopeS2CToTrackingChunk(envelope: CooPacketEnvelopeS2C, world: ServerLevel, chunk: ChunkPos) {
        val players = world.getChunkSource().chunkMap.getPlayers(chunk.x, chunk.z, false)
        players.forEach { channel.sendTo(it as ServerPlayer, envelope) }
    }

    fun sendEnvelopeC2S(packet: CooPacketEnvelopeC2S) {
        channel.sendToServer(packet)
    }
}
