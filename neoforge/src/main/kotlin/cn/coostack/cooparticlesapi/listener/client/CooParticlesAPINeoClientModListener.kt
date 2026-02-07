package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.client.KeyBindingManager
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientModListener {
    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        KeyBindingManager.setRegistrar { mapping ->
            event.register(mapping)
        }
    }
}
