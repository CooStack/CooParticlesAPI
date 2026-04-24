package cn.coostack.cooparticlesapi.event.events.world.server

import cn.coostack.cooparticlesapi.event.events.world.ServerWorldEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

class ServerWorldPostTickEvent(world: ServerLevel, server: MinecraftServer) : ServerWorldEvent(world, server)