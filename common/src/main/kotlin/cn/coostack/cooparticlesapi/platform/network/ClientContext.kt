package cn.coostack.cooparticlesapi.platform.network

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player

interface ClientContext {
    fun player(): Player
    fun client(): Minecraft
}