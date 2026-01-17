package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableCloudParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableEnchantmentParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableEndRodParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableFallingDustParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableFireworkParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableFlashParticle
import cn.coostack.cooparticlesapi.particles.impl.particles.ControlableSplashParticle
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.registries.RegisterEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesNeoClientInitialize {
    @SubscribeEvent
    fun init(event: FMLClientSetupEvent) {
        CooParticlesAPIClient.init()
    }


    @SubscribeEvent
    fun onClientRegistry(event: RegisterEvent) {
        CooModParticles.reg()
    }

    @SubscribeEvent
    fun onParticleInit(event: RegisterParticleProvidersEvent) {
        event.registerSpriteSet(CooModParticles.controlableEndRod.get()) { ControlableEndRodParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableCloud.get()) { ControlableCloudParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableFlash.get()) { ControlableFlashParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableFirework.get()) { ControlableFireworkParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableSplash.get()) { ControlableSplashParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableEnchantment.get()) {
            ControlableEnchantmentParticle.Factory(
                it
            )
        }
        event.registerSpriteSet(CooModParticles.controlableFallingDust.get()) { ControlableFallingDustParticle.Factory() }
    }

}