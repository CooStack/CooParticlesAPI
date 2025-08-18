package cn.coostack.cooparticlesapi.platform.network

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.handling.IPayloadContext

class NeoForgeClientContext(val context: IPayloadContext) : ClientContext {
    override fun player(): Player {
        return context.player()
    }

    override fun client(): Minecraft {
        return Minecraft.getInstance()
    }
}