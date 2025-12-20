package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.network.packet.*
import cn.coostack.cooparticlesapi.network.packet.client.listener.*
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.*
import cn.coostack.cooparticlesapi.platform.network.FabricClientContext
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry


object CooParticlesAPIFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        registerParticleFabric()
        registerNetworkFabric()
        CooParticlesAPIClient.init()
        initEvents()
    }

    private fun initEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, event ->
            CooParticlesAPIClient.onDisconnect()
        }
        ClientTickEvents.START_WORLD_TICK.register {
            CooParticlesAPIClient.tickClient(it)
        }
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, _ ->
            CooParticlesAPIClient.afterClientWorldChange()
        }


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
    }

    private fun registerNetworkFabric() {
        ClientPlayNetworking.registerGlobalReceiver(PacketRenderEntityS2C.payloadID) { payload, context ->
            ClientRenderEntityPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleGroupS2C.payloadID) { payload, context ->
            ClientParticleGroupPacketHandler.receive(payload, FabricClientContext(context))
        }
        ClientPlayNetworking.registerGlobalReceiver(PacketParticleEmittersS2C.payloadID) { payload, context ->
            ClientParticleEmittersPacketHandler.receive(payload, FabricClientContext(context))
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
    }
}