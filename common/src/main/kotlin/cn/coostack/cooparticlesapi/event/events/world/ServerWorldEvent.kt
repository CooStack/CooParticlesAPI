package cn.coostack.cooparticlesapi.event.events.world

import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

abstract class ServerWorldEvent(val world: ServerLevel, server: MinecraftServer) : ServerEvent(server)