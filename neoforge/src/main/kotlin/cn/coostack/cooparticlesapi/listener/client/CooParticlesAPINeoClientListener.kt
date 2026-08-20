package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientPreTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientStartEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import cn.coostack.cooparticlesapi.performance.client.PerformanceStatusClientController
import cn.coostack.cooparticlesapi.performance.client.PerformanceStatusKeyBindings
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.LevelEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientListener {

    @SubscribeEvent
    fun onDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        CooParticlesAPIClient.onDisconnect()
    }

    @SubscribeEvent
    fun onWorldChange(event: LevelEvent.Load) {
        if (event.level !is ClientLevel) {
            return
        }
        CooParticlesAPIClient.afterClientWorldChange()
        CooEventBus.call(ClientWorldChangeEvent(event.level as ClientLevel))
    }


    @SubscribeEvent
    fun tickClientPre(event: ClientTickEvent.Pre) {
        CooKeyBindingManager.tick()
        val e = ClientPreTickEvent(Minecraft.getInstance())
        CooEventBus.call(e)
    }

    @SubscribeEvent
    fun tickClientPost(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        val e = ClientPostTickEvent(client)
        CooEventBus.call(e)
        PerformanceStatusKeyBindings.tick(client)
        PerformanceStatusClientController.onClientTick(client)
    }

    @SubscribeEvent
    fun onClientStart(event: FMLClientSetupEvent) {
        CooEventBus.call(ClientStartEvent(Minecraft.getInstance()))
    }
}
