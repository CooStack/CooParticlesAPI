package cn.coostack.cooparticlesapi.platform

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.neoforged.neoforge.client.network.ClientPacketDistributor

class NeoForgeClientNetworking : ClientNetworking {
    override fun send(packet: CustomPacketPayload) {
        ClientPacketDistributor.sendToServer(packet)
    }

}
