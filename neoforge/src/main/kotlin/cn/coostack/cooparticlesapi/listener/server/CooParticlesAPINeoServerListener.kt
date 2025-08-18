package cn.coostack.cooparticlesapi.listener.server

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.CooParticlesConstants
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
    fun onServerTick(event: ServerTickEvent.Post) {
        CooParticlesAPI.tickServer(event.server)
    }
}