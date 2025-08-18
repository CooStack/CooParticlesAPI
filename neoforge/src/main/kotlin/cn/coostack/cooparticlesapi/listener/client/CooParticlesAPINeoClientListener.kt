package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientListener {
    @SubscribeEvent
    fun tickClient(event: LevelTickEvent.Post) {
        if (!event.level.isClientSide) {
            return
        }
        CooParticlesAPIClient.tickClient(event.level as ClientLevel)
    }

    @SubscribeEvent
    fun onDisconnect(event: PlayerEvent.PlayerLoggedOutEvent) {
        CooParticlesAPIClient.onDisconnect()
    }

    @SubscribeEvent
    fun onWorldChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        CooParticlesAPIClient.afterClientWorldChange()
    }

}