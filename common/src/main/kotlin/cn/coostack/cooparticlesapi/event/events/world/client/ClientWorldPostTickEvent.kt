package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.client.ClientEvent
import cn.coostack.cooparticlesapi.event.events.world.WorldEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

class ClientWorldPostTickEvent(world: ClientLevel) : WorldEvent(world)