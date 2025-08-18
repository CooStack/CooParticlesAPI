package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.datagen.CooItemModelProvider
import cn.coostack.cooparticlesapi.datagen.LangProvider
import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientCameraShakeHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleEmittersPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleGroupPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticlePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleStylePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientRenderEntityPacketHandler
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.particles.impl.ControlableCloudParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableEnchantmentParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableFireworkParticle
import cn.coostack.cooparticlesapi.particles.impl.ControlableFlashParticle
import cn.coostack.cooparticlesapi.platform.network.NeoForgeClientContext
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
)
object CooParticlesAPINeoModInitListener {

    @SubscribeEvent
    fun onParticleInit(event: RegisterParticleProvidersEvent) {
        event.registerSpriteSet(CooModParticles.endRod.get()) { ControlableEndRodParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableCloud.get()) { ControlableCloudParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableFlash.get()) { ControlableFlashParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.controlableFirework.get()) { ControlableFireworkParticle.Factory(it) }
        event.registerSpriteSet(CooModParticles.enchantment.get()) { ControlableEnchantmentParticle.Factory(it) }
    }

    @SubscribeEvent
    fun onDataProvider(event: GatherDataEvent) {
        val generator = event.generator
        val helper = event.existingFileHelper
        generator.addProvider(true) {
            CooItemModelProvider(it, helper)
        }
        generator.addProvider(true) {
            LangProvider(it)
        }
    }

    @SubscribeEvent
    fun onPacketReceiverInit(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CooParticlesConstants.MOD_ID)
        CooParticlesConstants.logger.info("正在注册数据包 register packets")
        registrar.playToClient(
            PacketRenderEntityS2C.payloadID,
            PacketRenderEntityS2C.CODEC
        ) { payload, context ->
            ClientRenderEntityPacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleGroupS2C.payloadID,
            PacketParticleGroupS2C.CODEC
        ) { payload, context ->
            ClientParticleGroupPacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleEmittersS2C.payloadID,
            PacketParticleEmittersS2C.CODEC
        ) { payload, context ->
            ClientParticleEmittersPacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleStyleS2C.payloadID,
            PacketParticleStyleS2C.CODEC
        ) { payload, context ->
            ClientParticleStylePacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleS2C.payloadID,
            PacketParticleS2C.CODEC
        ) { payload, context ->
            ClientParticlePacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketCameraShakeS2C.payloadID,
            PacketCameraShakeS2C.CODEC
        ) { payload, context ->
            ClientCameraShakeHandler.receive(payload, NeoForgeClientContext(context))
        }
    }
}