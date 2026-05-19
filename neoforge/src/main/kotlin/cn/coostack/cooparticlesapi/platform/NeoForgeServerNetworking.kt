package cn.coostack.cooparticlesapi.platform

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.neoforged.neoforge.network.PacketDistributor

class NeoForgeServerNetworking : ServerNetworking {
    override fun send(
        packet: CustomPacketPayload,
        to: ServerPlayer
    ) {
        PacketDistributor.sendToPlayer(to, packet)
    }

    override fun sendAllPlayers(packet: CustomPacketPayload) {
        PacketDistributor.sendToAllPlayers(packet)
    }

    override fun sendToPlayersTrackingChunk(
        world: ServerLevel,
        chunk: ChunkPos,
        packet: CustomPacketPayload
    ) {
        PacketDistributor.sendToPlayersTrackingChunk(world, chunk, packet)
    }

    override fun sendToWorld(world: ServerLevel, packet: CustomPacketPayload) {
        PacketDistributor.sendToPlayersInDimension(world, packet)
    }

}