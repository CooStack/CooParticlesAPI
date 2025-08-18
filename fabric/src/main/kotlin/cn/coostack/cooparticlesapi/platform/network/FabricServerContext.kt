package cn.coostack.cooparticlesapi.platform.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player

class FabricServerContext(val context: ServerPlayNetworking.Context) : ServerContext {
    override fun player(): Player {
        return context.player()
    }

    override fun server(): MinecraftServer {
        return context.server()
    }

    override fun reply(packet: CustomPacketPayload) {
        context.responseSender().sendPacket(packet)
    }
}