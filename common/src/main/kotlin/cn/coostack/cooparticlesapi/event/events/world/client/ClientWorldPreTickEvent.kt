package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.world.ClientWorldEvent
import net.minecraft.client.multiplayer.ClientLevel

class ClientWorldPreTickEvent(world: ClientLevel) : ClientWorldEvent(world) {
}