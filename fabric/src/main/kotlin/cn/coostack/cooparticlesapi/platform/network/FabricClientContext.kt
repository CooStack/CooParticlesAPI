package cn.coostack.cooparticlesapi.platform.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player

class FabricClientContext(val context: ClientPlayNetworking.Context) : ClientContext {
    override fun player(): Player {
        return context.player()
    }

    override fun client(): Minecraft {
        return context.client()
    }
}