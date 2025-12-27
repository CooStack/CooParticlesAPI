package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.client.ClientEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

class ClientWorldPostTickEvent(val world: ClientLevel) : ClientEvent(Minecraft.getInstance()) {
}