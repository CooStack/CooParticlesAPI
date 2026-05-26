package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.particles.*
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.particles.ParticleType
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
        registerSimpleParticle(event, CooModParticles.controlableAngryVillager)
        registerSimpleParticle(event, CooModParticles.controlableBubble)
        registerSimpleParticle(event, CooModParticles.controlableBubbleColumnUp)
        registerSimpleParticle(event, CooModParticles.controlableBubblePop)
        registerSimpleParticle(event, CooModParticles.controlableCampfireCosySmoke)
        registerSimpleParticle(event, CooModParticles.controlableCampfireSignalSmoke)
        registerSimpleParticle(event, CooModParticles.controlableComposter)
        registerSimpleParticle(event, CooModParticles.controlableCrit)
        registerSimpleParticle(event, CooModParticles.controlableCurrentDown)
        registerSimpleParticle(event, CooModParticles.controlableDamageIndicator)
        registerSimpleParticle(event, CooModParticles.controlableDragonBreath)
        registerSimpleParticle(event, CooModParticles.controlableDolphin)
        registerSimpleParticle(event, CooModParticles.controlableDrippingLava)
        registerSimpleParticle(event, CooModParticles.controlableFallingLava)
        registerSimpleParticle(event, CooModParticles.controlableLandingLava)
        registerSimpleParticle(event, CooModParticles.controlableDrippingWater)
        registerSimpleParticle(event, CooModParticles.controlableFallingWater)
        registerSimpleParticle(event, CooModParticles.controlableEffect)
        registerSimpleParticle(event, CooModParticles.controlableEnchantedHit)
        registerSimpleParticle(event, CooModParticles.controlableExplosion)
        registerSimpleParticle(event, CooModParticles.controlableSonicBoom)
        registerSimpleParticle(event, CooModParticles.controlableGust)
        registerSimpleParticle(event, CooModParticles.controlableSmallGust)
        registerSimpleParticle(event, CooModParticles.controlableFishing)
        registerSimpleParticle(event, CooModParticles.controlableFlame)
        registerSimpleParticle(event, CooModParticles.controlableInfested)
        registerSimpleParticle(event, CooModParticles.controlableCherryLeaves)
        registerSimpleParticle(event, CooModParticles.controlableSculkSoul)
        registerSimpleParticle(event, CooModParticles.controlableSculkChargePop)
        registerSimpleParticle(event, CooModParticles.controlableSoul)
        registerSimpleParticle(event, CooModParticles.controlableSoulFireFlame)
        registerSimpleParticle(event, CooModParticles.controlableHappyVillager)
        registerSimpleParticle(event, CooModParticles.controlableHeart)
        registerSimpleParticle(event, CooModParticles.controlableInstantEffect)
        registerSimpleParticle(event, CooModParticles.controlableLargeSmoke)
        registerSimpleParticle(event, CooModParticles.controlableLava)
        registerSimpleParticle(event, CooModParticles.controlableMycelium)
        registerSimpleParticle(event, CooModParticles.controlableNautilus)
        registerSimpleParticle(event, CooModParticles.controlableNote)
        registerSimpleParticle(event, CooModParticles.controlablePoof)
        registerSimpleParticle(event, CooModParticles.controlablePortal)
        registerSimpleParticle(event, CooModParticles.controlableRain)
        registerSimpleParticle(event, CooModParticles.controlableSmoke)
        registerSimpleParticle(event, CooModParticles.controlableWhiteSmoke)
        registerSimpleParticle(event, CooModParticles.controlableSneeze)
        registerSimpleParticle(event, CooModParticles.controlableSnowflake)
        registerSimpleParticle(event, CooModParticles.controlableSpit)
        registerSimpleParticle(event, CooModParticles.controlableSweepAttack)
        registerSimpleParticle(event, CooModParticles.controlableTotemOfUndying)
        registerSimpleParticle(event, CooModParticles.controlableSquidInk)
        registerSimpleParticle(event, CooModParticles.controlableUnderwater)
        registerSimpleParticle(event, CooModParticles.controlableWitch)
        registerSimpleParticle(event, CooModParticles.controlableDrippingHoney)
        registerSimpleParticle(event, CooModParticles.controlableFallingHoney)
        registerSimpleParticle(event, CooModParticles.controlableLandingHoney)
        registerSimpleParticle(event, CooModParticles.controlableFallingNectar)
        registerSimpleParticle(event, CooModParticles.controlableFallingSporeBlossom)
        registerSimpleParticle(event, CooModParticles.controlableSporeBlossomAir)
        registerSimpleParticle(event, CooModParticles.controlableAsh)
        registerSimpleParticle(event, CooModParticles.controlableCrimsonSpore)
        registerSimpleParticle(event, CooModParticles.controlableWarpedSpore)
        registerSimpleParticle(event, CooModParticles.controlableDrippingObsidianTear)
        registerSimpleParticle(event, CooModParticles.controlableFallingObsidianTear)
        registerSimpleParticle(event, CooModParticles.controlableLandingObsidianTear)
        registerSimpleParticle(event, CooModParticles.controlableReversePortal)
        registerSimpleParticle(event, CooModParticles.controlableWhiteAsh)
        registerSimpleParticle(event, CooModParticles.controlableSmallFlame)
        registerSimpleParticle(event, CooModParticles.controlableDrippingDripstoneWater)
        registerSimpleParticle(event, CooModParticles.controlableFallingDripstoneWater)
        registerSimpleParticle(event, CooModParticles.controlableDrippingDripstoneLava)
        registerSimpleParticle(event, CooModParticles.controlableFallingDripstoneLava)
        registerSimpleParticle(event, CooModParticles.controlableGlowSquidInk)
        registerSimpleParticle(event, CooModParticles.controlableGlow)
        registerSimpleParticle(event, CooModParticles.controlableWaxOn)
        registerSimpleParticle(event, CooModParticles.controlableWaxOff)
        registerSimpleParticle(event, CooModParticles.controlableElectricSpark)
        registerSimpleParticle(event, CooModParticles.controlableScrape)
        registerSimpleParticle(event, CooModParticles.controlableEggCrack)
        registerSimpleParticle(event, CooModParticles.controlableDustPlume)
        registerSimpleParticle(event, CooModParticles.controlableTrialSpawnerDetection)
        registerSimpleParticle(event, CooModParticles.controlableTrialSpawnerDetectionOminous)
        registerSimpleParticle(event, CooModParticles.controlableVaultConnection)
        registerSimpleParticle(event, CooModParticles.controlableRaidOmen)
        registerSimpleParticle(event, CooModParticles.controlableTrialOmen)
        registerSimpleParticle(event, CooModParticles.controlableOminousSpawning)
    }

    private fun <T : ControlableParticleEffect> registerSimpleParticle(
        event: RegisterParticleProvidersEvent,
        type: CommonDeferredRegistry<ParticleType<T>>
    ) {
        event.registerSpriteSet(type.get()) { SimpleControlableSpriteParticle.Factory<T>(it) }
    }

}
