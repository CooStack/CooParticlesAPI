package cn.coostack.cooparticlesapi.listener.server

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.server.ServerPostTickEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerPreTickEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID
)
object CooParticlesAPINeoServerListener {
    @SubscribeEvent
    fun onServerLoad(event: ServerStartingEvent) {
        val server = event.server
        CooParticlesAPI.onServerStart(server)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Pre) {
        CooParticlesAPI.tickServer(event.server)
        CooEventBus.call(
            ServerPreTickEvent(event.server)
        )
    }

    @SubscribeEvent
    fun onServerStart(event: ServerTickEvent.Post) {
        CooEventBus.call(
            ServerPostTickEvent(event.server)
        )
    }
}