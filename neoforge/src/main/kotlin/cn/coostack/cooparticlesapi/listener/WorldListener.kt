package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPreTickEvent
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPreTickEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber
object WorldListener {
    @SubscribeEvent
    fun tickWorldPost(event: LevelTickEvent.Post) {
        if (event.level.isClientSide) {
            val event = ClientWorldPostTickEvent(event.level as ClientLevel)
            CooEventBus.call(event)
            return
        }
        val level = event.level as ServerLevel
        val event = ServerWorldPostTickEvent(level, level.server)
        CooEventBus.call(event)
    }

    @SubscribeEvent
    fun tickWorldPre(event: LevelTickEvent.Pre) {
        if (event.level.isClientSide) {
            CooParticlesAPIClient.tickClient(event.level as ClientLevel)
            val event = ClientWorldPreTickEvent(event.level as ClientLevel)
            CooEventBus.call(event)
            return
        }
        val level = event.level as ServerLevel
        val event = ServerWorldPreTickEvent(level, level.server)
        CooEventBus.call(event)
    }

}
