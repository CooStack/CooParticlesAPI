package cn.coostack.cooparticlesapi.event.events.client

import cn.coostack.cooparticlesapi.event.api.CooEvent
import net.minecraft.client.Minecraft

abstract class ClientEvent(val client: Minecraft) : CooEvent()