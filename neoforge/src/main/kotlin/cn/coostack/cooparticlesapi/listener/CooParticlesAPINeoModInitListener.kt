package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.datagen.CooItemModelProvider
import cn.coostack.cooparticlesapi.datagen.LangProvider
import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketClearClientStateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketDataHolderS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketDisplayEntityStateS2C
import cn.coostack.cooparticlesapi.network.packet.client.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionRotateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleCompositionStateS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleBatchS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketRendererPostEffectS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundLoopS2C
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeC2S
import cn.coostack.cooparticlesapi.network.packet.api.envelope.CooPacketEnvelopeS2C
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientCameraShakeHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientClearStateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientDataHolderPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientDisplayEntityPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientDisplayEntityStateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientKeyBindingCountdownHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleCompositionHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleCompositionStateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleCompositionRotateHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleEmittersPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleGroupPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticlePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientParticleStylePacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientRenderEntityPacketHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientRendererPostEffectHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientSoundInstanceHandler
import cn.coostack.cooparticlesapi.network.packet.client.listener.ClientSoundLoopHandler
import cn.coostack.cooparticlesapi.network.packet.server.PacketKeyBindingCountdownS2C
import cn.coostack.cooparticlesapi.network.packet.server.listener.ServerKeyActionHandler
import cn.coostack.cooparticlesapi.platform.network.NeoForgeClientContext
import cn.coostack.cooparticlesapi.platform.network.NeoForgeServerContext
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
)
object CooParticlesAPINeoModInitListener {
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
            PacketRendererPostEffectS2C.payloadID,
            PacketRendererPostEffectS2C.CODEC
        ) { payload, context ->
            ClientRendererPostEffectHandler.receive(payload, NeoForgeClientContext(context))
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
            PacketDataHolderS2C.payloadID,
            PacketDataHolderS2C.CODEC
        ) { payload, context ->
            ClientDataHolderPacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleCompositionS2C.payloadID,
            PacketParticleCompositionS2C.CODEC
        ) { payload, context ->
            ClientParticleCompositionHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleCompositionStateS2C.payloadID,
            PacketParticleCompositionStateS2C.CODEC
        ) { payload, context ->
            ClientParticleCompositionStateHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketParticleCompositionRotateS2C.payloadID,
            PacketParticleCompositionRotateS2C.CODEC
        ) { payload, context ->
            ClientParticleCompositionRotateHandler.receive(payload, NeoForgeClientContext(context))
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
            PacketParticleBatchS2C.payloadID,
            PacketParticleBatchS2C.CODEC
        ) { payload, context ->
            ClientParticlePacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketCameraShakeS2C.payloadID,
            PacketCameraShakeS2C.CODEC
        ) { payload, context ->
            ClientCameraShakeHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketClearClientStateS2C.payloadID,
            PacketClearClientStateS2C.CODEC
        ) { payload, context ->
            ClientClearStateHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketDisplayEntityS2C.payloadID,
            PacketDisplayEntityS2C.CODEC
        ) { payload, context ->
            ClientDisplayEntityPacketHandler.receive(payload, NeoForgeClientContext(context))
        }
        registrar.playToClient(
            PacketDisplayEntityStateS2C.payloadID,
            PacketDisplayEntityStateS2C.CODEC
        ) { payload, context ->
            ClientDisplayEntityStateHandler.receive(payload, NeoForgeClientContext(context))
        }

        registrar.playToClient(
            PacketKeyBindingCountdownS2C.payloadID,
            PacketKeyBindingCountdownS2C.CODEC
        ) { payload, context ->
            ClientKeyBindingCountdownHandler.receive(payload, NeoForgeClientContext(context))
        }

        registrar.playToClient(
            PacketSoundLoopS2C.payloadID,
            PacketSoundLoopS2C.CODEC
        ) { payload, context ->
            ClientSoundLoopHandler.receive(payload, NeoForgeClientContext(context))
        }

        registrar.playToClient(
            PacketSoundInstanceS2C.payloadID,
            PacketSoundInstanceS2C.CODEC
        ) { payload, context ->
            ClientSoundInstanceHandler.receive(payload, NeoForgeClientContext(context))
        }

        registrar.playToServer(
            PacketKeyActionC2S.payloadID,
            PacketKeyActionC2S.CODEC
        ) { payload, context ->
            ServerKeyActionHandler.receive(payload, NeoForgeServerContext(context))
        }

        registrar.playToClient(
            CooPacketEnvelopeS2C.payloadID,
            CooPacketEnvelopeS2C.CODEC
        ) { payload, _ ->
            CooClientPacketManager.handleS2C(payload)
        }

        registrar.playToServer(
            CooPacketEnvelopeC2S.payloadID,
            CooPacketEnvelopeC2S.CODEC
        ) { payload, context ->
            val player = context.player()
            if (player is net.minecraft.server.level.ServerPlayer) {
                CooServerPacketManager.handleC2S(payload, player)
            }
        }

    }
}
