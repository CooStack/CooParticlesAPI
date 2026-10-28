package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.CooShaderReloadListenerNeo
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.entities.structure.editor.client.StructureEditorClient
import cn.coostack.cooparticlesapi.performance.client.PerformanceStatusKeyBindings
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT],
)
object CooParticlesAPINeoClientRegistration {
    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        CooKeyBindingManager.setRegistrar { mapping ->
            event.register(mapping)
        }
        StructureEditorClient.registerKeys()
        PerformanceStatusKeyBindings.mappings().forEach(event::register)
    }

    @SubscribeEvent
    fun onRegisterClientReloadListeners(event: AddClientReloadListenersEvent) {
        event.addListener(
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "shader_reload"),
            CooShaderReloadListenerNeo
        )
    }
}
