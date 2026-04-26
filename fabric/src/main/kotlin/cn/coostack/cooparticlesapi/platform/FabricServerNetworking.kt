package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesAPIFabric
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

class FabricServerNetworking : ServerNetworking {
    override fun send(
        packet: CustomPacketPayload,
        to: ServerPlayer
    ) {
        ServerPlayNetworking.send(to, packet)
    }

    override fun sendAllPlayers(packet: CustomPacketPayload) {
        CooParticlesAPIFabric.server.playerList.players.forEach {
            ServerPlayNetworking.send(it, packet)
        }
    }

    override fun sendToPlayersTrackingChunk(
        world: ServerLevel,
        chunk: ChunkPos,
        packet: CustomPacketPayload
    ) {
        PlayerLookup.tracking(world, chunk).forEach { player ->
            ServerPlayNetworking.send(player, packet)
        }
    }

}
