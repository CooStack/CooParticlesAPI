package cn.coostack.cooparticlesapi.platform

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

interface ServerNetworking {
    fun send(packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket, to: ServerPlayer)
    fun sendAllPlayers(packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket)
    fun sendToPlayersTrackingChunk(world: ServerLevel, chunk: ChunkPos, packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket)

    fun sendToWorld(world: ServerLevel, packet: cn.coostack.cooparticlesapi.network.packet.api.CooPacket) {
        world.players().forEach { send(packet, it) }
    }
}