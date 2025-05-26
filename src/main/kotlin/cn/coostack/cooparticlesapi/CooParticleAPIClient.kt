package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleEmittersPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleGroupPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticlePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleStylePacketHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableFireworkParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashParticle
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodParticle
import cn.coostack.cooparticlesapi.test.entity.CooParticleEntities
import cn.coostack.cooparticlesapi.test.entity.CooParticlesEntityLayers
import cn.coostack.cooparticlesapi.test.entity.TestEntity
import cn.coostack.cooparticlesapi.test.entity.TestEntityModel
import cn.coostack.cooparticlesapi.test.entity.TestEntityRenderer
import cn.coostack.cooparticlesapi.test.entity.TestPlayerEntityRenderer
import cn.coostack.cooparticlesapi.test.entity.TestPlayerModel
import cn.coostack.cooparticlesapi.test.particle.client.BarrierSwordGroupClient
import cn.coostack.cooparticlesapi.test.particle.client.ScaleCircleGroupClient
import cn.coostack.cooparticlesapi.test.particle.client.SequencedMagicCircleClient
import cn.coostack.cooparticlesapi.test.particle.client.TestGroupClient
import cn.coostack.cooparticlesapi.test.particle.style.ExampleSequencedStyle
import cn.coostack.cooparticlesapi.test.particle.style.ExampleStyle
import cn.coostack.cooparticlesapi.test.particle.style.RomaMagicTestStyle
import cn.coostack.cooparticlesapi.test.particle.style.RotateTestStyle
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

object CooParticleAPIClient : ClientModInitializer {

    override fun onInitializeClient() {
        loadParticleGroupPacketListener()
        registerClientEvents()
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.testEndRod, ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory TestEndRodParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.enchantment, ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableEnchantmentParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableCloud, ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableCloudParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableFlash, ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableFlashParticle.Factory(it)
            })
        ParticleFactoryRegistry.getInstance()
            .register(CooModParticles.controlableFirework, ParticleFactoryRegistry.PendingParticleFactory {
                return@PendingParticleFactory ControlableFireworkParticle.Factory(it)
            })
        ClientParticleGroupManager.register(
            TestGroupClient::class.java, TestGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            ScaleCircleGroupClient::class.java, ScaleCircleGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            BarrierSwordGroupClient::class.java, BarrierSwordGroupClient.Provider()
        )
        ClientParticleGroupManager.register(
            SequencedMagicCircleClient::class.java, SequencedMagicCircleClient.Provider()
        )

        ParticleStyleManager.register(ExampleStyle::class.java, ExampleStyle.Provider())
        ParticleStyleManager.register(ExampleSequencedStyle::class.java, ExampleSequencedStyle.Provider())
        ParticleStyleManager.register(RomaMagicTestStyle::class.java, RomaMagicTestStyle.Provider())
        ParticleStyleManager.register(RotateTestStyle::class.java, RotateTestStyle.Provider())
        CooModParticles.reg()
        ParticleEmittersManager.init()
        testEntity()
    }


    private fun loadParticleGroupPacketListener() {
        ClientPlayNetworking.registerGlobalReceiver(
            PacketParticleGroupS2C.payloadID,
            ClientParticleGroupPacketHandler
        )
        ClientPlayNetworking.registerGlobalReceiver(
            PacketParticleEmittersS2C.payloadID,
            ClientParticleEmittersPacketHandler
        )
        ClientPlayNetworking.registerGlobalReceiver(
            PacketParticleStyleS2C.payloadID,
            ClientParticleStylePacketHandler
        )
        ClientPlayNetworking.registerGlobalReceiver(
            PacketParticleS2C.payloadID,
            ClientParticlePacketHandler
        )
    }


    private fun registerClientEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, event ->
            ParticleEmittersManager.clientEmitters.clear()
            ParticleStyleManager.clearAllVisible()
            ClientParticleGroupManager.clearAllVisible()
        }
        ClientTickEvents.START_WORLD_TICK.register {
            ClientParticleGroupManager.doClientTick()
            ParticleStyleManager.doTickClient()
            ParticleEmittersManager.doTickClient()
        }
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, _ ->
            ParticleEmittersManager.clientEmitters.clear()
            ParticleStyleManager.clearAllVisible()
            ClientParticleGroupManager.clearAllVisible()
        }
    }

    private fun testEntity() {
        EntityModelLayerRegistry.registerModelLayer(
            CooParticlesEntityLayers.TEST_ENTITY_LAYER,
            TestEntityModel<TestEntity>::getTexturedModelData
        )
        EntityRendererRegistry.register(CooParticleEntities.TEST_ENTITY, ::TestEntityRenderer)

        EntityModelLayerRegistry.registerModelLayer(
            CooParticlesEntityLayers.TEST_PLAYER_ENTITY_LAYER,
            TestPlayerModel::getTexturedModelData
        )
        EntityRendererRegistry.register(CooParticleEntities.TEST_PLAYER_ENTITY, ::TestPlayerEntityRenderer)
    }
}