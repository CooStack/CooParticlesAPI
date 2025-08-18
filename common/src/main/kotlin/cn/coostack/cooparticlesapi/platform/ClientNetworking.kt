package cn.coostack.cooparticlesapi.platform

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

interface ClientNetworking {

    fun send(packet: CustomPacketPayload)


}