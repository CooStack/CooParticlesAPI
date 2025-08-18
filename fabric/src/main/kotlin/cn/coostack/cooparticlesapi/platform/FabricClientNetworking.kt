package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesAPIFabric
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos

class FabricClientNetworking : ClientNetworking {


    override fun send(packet: CustomPacketPayload) {
        ClientPlayNetworking.send(packet)
    }


}