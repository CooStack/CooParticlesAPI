package cn.coostack.cooparticlesapi.platform.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player

interface ServerContext {
    fun player(): Player
    fun server(): MinecraftServer
    fun reply(packet: CustomPacketPayload)
}