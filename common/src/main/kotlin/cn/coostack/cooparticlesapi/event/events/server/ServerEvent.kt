package cn.coostack.cooparticlesapi.event.events.server

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.server.MinecraftServer

abstract class ServerEvent(val server: MinecraftServer) : CooEvent()