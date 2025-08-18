package cn.coostack.cooparticlesapi.platform.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.handling.IPayloadContext

class NeoForgeServerContext(val context: IPayloadContext) : ServerContext {
    override fun player(): Player {
        return context.player()
    }

    override fun server(): MinecraftServer {
        return player().server!!
    }

    override fun reply(packet: CustomPacketPayload) {
        context.reply(packet)
    }
}