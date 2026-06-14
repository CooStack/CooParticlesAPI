package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.entities.renderer.TestRenderEntityRenderer
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientPreTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientStartEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldPreTickEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeS2C
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientCameraShakeHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientClearStateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientDataHolderPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientDisplayEntityPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientKeyBindingCountdownHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleCompositionHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleCompositionRotateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleEmittersPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleGroupPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticlePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleStylePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientRenderEntityPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientRendererPostEffectHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientSoundInstanceHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientSoundLoopHandler
import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketClearClientStateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketDataHolderS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketKeyBindingCountdownS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionRotateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketRendererPostEffectS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundLoopS2C
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.particles.*
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import cn.coostack.cooparticlesapi.platform.network.FabricClientContext
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.core.particles.ParticleType


object CooParticlesAPIFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CooKeyBindingManager.setRegistrar { KeyBindingHelper.registerKeyBinding(it) }
        CooShaderReloadListenerFabric.register()
        registerParticleFabric()
        registerNetworkFabric()
        CooParticlesAPIClient.init()
        initEvents()
        registerEntityRenderer()
    }

    private fun initEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CooParticlesAPIClient.onDisconnect()
        }
        ClientTickEvents.START_WORLD_TICK.register {
            CooParticlesAPIClient.tickClient(it)
            val event = ClientWorldPreTickEvent(it)
            CooEventBus.call(event)
        }
        ClientTickEvents.END_WORLD_TICK.register {
            val event = ClientWorldPostTickEvent(it)
            CooEventBus.call(event)
        }

        ClientTickEvents.START_CLIENT_TICK.register {
            CooKeyBindingManager.tick()
            val event = ClientPreTickEvent(it)
            CooEventBus.call(event)
        }

        /**
         * 兼容 sodium
         */
        WorldRenderEvents.AFTER_ENTITIES.register {
            CooEventBus.call(
                ClientWorldRenderEvent(
                    it.world(), ClientWorldRenderEvent.RenderStage.AFTER_ENTITY,
                    it.positionMatrix(),
                    it.projectionMatrix(),
                    it.matrixStack() ?: return@register,
                    it.consumers() ?: return@register,
                    it.worldRenderer(),
                    it.camera(),
                    it.tickCounter()
                )
            )
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            val event = ClientPostTickEvent(it)
            CooEventBus.call(event)
        }

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, world ->
            CooParticlesAPIClient.afterClientWorldChange()
            CooEventBus.call(ClientWorldChangeEvent(world))
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { client ->
            CooParticlesConstants.logger.info("Client Started")
            CooAPIScanner.scan()
            CooParticlesAPI.loadScannerPackages()
            CooEventBus.call(ClientStartEvent(client))
        }
    }


    private fun registerEntityRenderer() {
        EntityRendererRegistry.register(CooModEntityTypes.TEST_RENDER.get(), ::TestRenderEntityRenderer)
    }

    private fun registerParticleFabric() {
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableEndRod.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableEndRodParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableEnchantment.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableEnchantmentParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableCloud.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableCloudParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableFlash.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableFlashParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableFirework.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableFireworkParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableFallingDust.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableFallingDustParticle.Factory()
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableSplash.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableSplashParticle.Factory(it)
            })
        registerSimpleParticle(CooModParticles.controlableAngryVillager)
        registerSimpleParticle(CooModParticles.controlableBubble)
        registerSimpleParticle(CooModParticles.controlableBubbleColumnUp)
        registerSimpleParticle(CooModParticles.controlableBubblePop)
        registerSimpleParticle(CooModParticles.controlableCampfireCosySmoke)
        registerSimpleParticle(CooModParticles.controlableCampfireSignalSmoke)
        registerSimpleParticle(CooModParticles.controlableComposter)
        registerSimpleParticle(CooModParticles.controlableCrit)
        registerSimpleParticle(CooModParticles.controlableCurrentDown)
        registerSimpleParticle(CooModParticles.controlableDamageIndicator)
        registerSimpleParticle(CooModParticles.controlableDragonBreath)
        registerSimpleParticle(CooModParticles.controlableDolphin)
        registerSimpleParticle(CooModParticles.controlableDrippingLava)
        registerSimpleParticle(CooModParticles.controlableFallingLava)
        registerSimpleParticle(CooModParticles.controlableLandingLava)
        registerSimpleParticle(CooModParticles.controlableDrippingWater)
        registerSimpleParticle(CooModParticles.controlableFallingWater)
        registerSimpleParticle(CooModParticles.controlableEffect)
        registerSimpleParticle(CooModParticles.controlableEnchantedHit)
        registerSimpleParticle(CooModParticles.controlableExplosion)
        registerSimpleParticle(CooModParticles.controlableSonicBoom)
        registerSimpleParticle(CooModParticles.controlableGust)
        registerSimpleParticle(CooModParticles.controlableSmallGust)
        registerSimpleParticle(CooModParticles.controlableFishing)
        registerSimpleParticle(CooModParticles.controlableFlame)
        registerSimpleParticle(CooModParticles.controlableInfested)
        registerSimpleParticle(CooModParticles.controlableCherryLeaves)
        registerSimpleParticle(CooModParticles.controlableSculkSoul)
        registerSimpleParticle(CooModParticles.controlableSculkChargePop)
        registerSimpleParticle(CooModParticles.controlableSoul)
        registerSimpleParticle(CooModParticles.controlableSoulFireFlame)
        registerSimpleParticle(CooModParticles.controlableHappyVillager)
        registerSimpleParticle(CooModParticles.controlableHeart)
        registerSimpleParticle(CooModParticles.controlableInstantEffect)
        registerSimpleParticle(CooModParticles.controlableLargeSmoke)
        registerSimpleParticle(CooModParticles.controlableLava)
        registerSimpleParticle(CooModParticles.controlableMycelium)
        registerSimpleParticle(CooModParticles.controlableNautilus)
        registerSimpleParticle(CooModParticles.controlableNote)
        registerSimpleParticle(CooModParticles.controlablePoof)
        registerSimpleParticle(CooModParticles.controlablePortal)
        registerSimpleParticle(CooModParticles.controlableRain)
        registerSimpleParticle(CooModParticles.controlableSmoke)
        registerSimpleParticle(CooModParticles.controlableWhiteSmoke)
        registerSimpleParticle(CooModParticles.controlableSneeze)
        registerSimpleParticle(CooModParticles.controlableSnowflake)
        registerSimpleParticle(CooModParticles.controlableSpit)
        registerSimpleParticle(CooModParticles.controlableSweepAttack)
        registerSimpleParticle(CooModParticles.controlableTotemOfUndying)
        registerSimpleParticle(CooModParticles.controlableSquidInk)
        registerSimpleParticle(CooModParticles.controlableUnderwater)
        registerSimpleParticle(CooModParticles.controlableWitch)
        registerSimpleParticle(CooModParticles.controlableDrippingHoney)
        registerSimpleParticle(CooModParticles.controlableFallingHoney)
        registerSimpleParticle(CooModParticles.controlableLandingHoney)
        registerSimpleParticle(CooModParticles.controlableFallingNectar)
        registerSimpleParticle(CooModParticles.controlableFallingSporeBlossom)
        registerSimpleParticle(CooModParticles.controlableSporeBlossomAir)
        registerSimpleParticle(CooModParticles.controlableAsh)
        registerSimpleParticle(CooModParticles.controlableCrimsonSpore)
        registerSimpleParticle(CooModParticles.controlableWarpedSpore)
        registerSimpleParticle(CooModParticles.controlableDrippingObsidianTear)
        registerSimpleParticle(CooModParticles.controlableFallingObsidianTear)
        registerSimpleParticle(CooModParticles.controlableLandingObsidianTear)
        registerSimpleParticle(CooModParticles.controlableReversePortal)
        registerSimpleParticle(CooModParticles.controlableWhiteAsh)
        registerSimpleParticle(CooModParticles.controlableSmallFlame)
        registerSimpleParticle(CooModParticles.controlableDrippingDripstoneWater)
        registerSimpleParticle(CooModParticles.controlableFallingDripstoneWater)
        registerSimpleParticle(CooModParticles.controlableDrippingDripstoneLava)
        registerSimpleParticle(CooModParticles.controlableFallingDripstoneLava)
        registerSimpleParticle(CooModParticles.controlableGlowSquidInk)
        registerSimpleParticle(CooModParticles.controlableGlow)
        registerSimpleParticle(CooModParticles.controlableWaxOn)
        registerSimpleParticle(CooModParticles.controlableWaxOff)
        registerSimpleParticle(CooModParticles.controlableElectricSpark)
        registerSimpleParticle(CooModParticles.controlableScrape)
        registerSimpleParticle(CooModParticles.controlableEggCrack)
        registerSimpleParticle(CooModParticles.controlableDustPlume)
        registerSimpleParticle(CooModParticles.controlableTrialSpawnerDetection)
        registerSimpleParticle(CooModParticles.controlableTrialSpawnerDetectionOminous)
        registerSimpleParticle(CooModParticles.controlableVaultConnection)
        registerSimpleParticle(CooModParticles.controlableRaidOmen)
        registerSimpleParticle(CooModParticles.controlableTrialOmen)
        registerSimpleParticle(CooModParticles.controlableOminousSpawning)
    }

    private fun <T : ControlableParticleEffect> registerSimpleParticle(type: CommonDeferredRegistry<ParticleType<T>>) {
        ParticleFactoryRegistry.getInstance()
            .register(type.get(), ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory SimpleControlableSpriteParticle.Factory<T>(it)
            })
    }

    private fun registerNetworkFabric() {
        ClientPlayNetworking.registerGlobalReceiver(PacketRenderEntityS2C.payloadID) { payload, context ->
            ClientRenderEntityPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketRendererPostEffectS2C.payloadID) { payload, context ->
            ClientRendererPostEffectHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleGroupS2C.payloadID) { payload, context ->
            ClientParticleGroupPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleEmittersS2C.payloadID) { payload, context ->
            ClientParticleEmittersPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleCompositionS2C.payloadID) { payload, context ->
            ClientParticleCompositionHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleCompositionRotateS2C.payloadID) { payload, context ->
            ClientParticleCompositionRotateHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleStyleS2C.payloadID) { payload, context ->
            ClientParticleStylePacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleS2C.payloadID) { payload, context ->
            ClientParticlePacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketCameraShakeS2C.payloadID) { payload, context ->
            ClientCameraShakeHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketClearClientStateS2C.payloadID) { payload, context ->
            ClientClearStateHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketDisplayEntityS2C.payloadID) { payload, context ->
            ClientDisplayEntityPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketDataHolderS2C.payloadID) { payload, context ->
            ClientDataHolderPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketKeyBindingCountdownS2C.payloadID) { payload, context ->
            ClientKeyBindingCountdownHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketSoundInstanceS2C.payloadID) { payload, context ->
            ClientSoundInstanceHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketSoundLoopS2C.payloadID) { payload, context ->
            ClientSoundLoopHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(CooPacketEnvelopeS2C.payloadID) { payload, _ ->
            CooClientPacketManager.handleS2C(payload)
        }
    }
}
