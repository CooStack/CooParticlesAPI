package cn.coostack.cooparticlesapi.platform

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

interface ServerNetworking {
    fun send(packet: CustomPacketPayload, to: ServerPlayer)
    fun sendAllPlayers(packet: CustomPacketPayload)
    fun sendToPlayersTrackingChunk(world: ServerLevel, chunk: ChunkPos, packet: CustomPacketPayload)

    /**
     * 把数据包发送给指定世界 (维度) 内的所有玩家
     */
    fun sendToWorld(world: ServerLevel, packet: CustomPacketPayload) {
        world.players().forEach { send(packet, it) }
    }
}