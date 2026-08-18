package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPreTickEvent
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientModListener {
    @SubscribeEvent
    fun tickWorldPre(event: LevelTickEvent.Pre) {
        if (!event.level.isClientSide) return
        val level = event.level as ClientLevel
        CooParticlesAPIClient.tickClient(level)
        CooEventBus.call(ClientWorldPreTickEvent(level))
    }

    @SubscribeEvent
    fun tickWorldPost(event: LevelTickEvent.Post) {
        if (!event.level.isClientSide) return
        val level = event.level as ClientLevel
        CooEventBus.call(ClientWorldPostTickEvent(level))
    }
}
