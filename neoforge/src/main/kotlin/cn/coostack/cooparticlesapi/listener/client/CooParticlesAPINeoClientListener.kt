package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientPreTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientStartEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPreTickEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientListener {

    @SubscribeEvent
    fun onDisconnect(event: PlayerEvent.PlayerLoggedOutEvent) {
        CooParticlesAPIClient.onDisconnect()
    }

    @SubscribeEvent
    fun onWorldChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        CooParticlesAPIClient.afterClientWorldChange()
        CooEventBus.call(ClientWorldChangeEvent(event.entity.level()))
    }


    @SubscribeEvent
    fun tickClientPre(event: ClientTickEvent.Pre) {
        CooKeyBindingManager.tick()
        val e = ClientPreTickEvent(Minecraft.getInstance())
        CooEventBus.call(e)
    }

    @SubscribeEvent
    fun tickClientPost(event: ClientTickEvent.Post) {
        val e = ClientPostTickEvent(Minecraft.getInstance())
        CooEventBus.call(e)
    }

    @SubscribeEvent
    fun onClientStart(event: FMLClientSetupEvent) {
        CooEventBus.call(ClientStartEvent())
    }
}
