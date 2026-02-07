package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.entity.EntityPrePlaceBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerDeathEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerRespawnEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerPostTickEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerPreTickEvent
import cn.coostack.cooparticlesapi.items.CooItemFabric
import cn.coostack.cooparticlesapi.items.group.CooItemGroup
import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.PacketDisplayEntityS2C
import cn.coostack.cooparticlesapi.network.packet.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.network.packet.PacketParticleCompositionS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.network.packet.server.listener.ServerKeyActionHandler
import cn.coostack.cooparticlesapi.platform.network.FabricServerContext
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.InteractionResult

object CooParticlesAPIFabric : ModInitializer {
    lateinit var server: MinecraftServer
    override fun onInitialize() {
        CooParticlesAPI.init()
        initPacket()
        initRegistries()
        initEvents()
    }

    private fun initPacket() {
        PayloadTypeRegistry.playS2C().register(PacketCameraShakeS2C.payloadID, PacketCameraShakeS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketParticleS2C.payloadID, PacketParticleS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketParticleEmittersS2C.payloadID, PacketParticleEmittersS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketParticleGroupS2C.payloadID, PacketParticleGroupS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketParticleStyleS2C.payloadID, PacketParticleStyleS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketRenderEntityS2C.payloadID, PacketRenderEntityS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketDisplayEntityS2C.payloadID, PacketDisplayEntityS2C.CODEC)
        PayloadTypeRegistry.playS2C()
            .register(PacketParticleCompositionS2C.payloadID, PacketParticleCompositionS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(PacketKeyActionC2S.payloadID, PacketKeyActionC2S.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(PacketKeyActionC2S.payloadID) { payload, context ->
            ServerKeyActionHandler.receive(payload, FabricServerContext(context))
        }
    }

    private fun initRegistries() {
        CooItemFabric.reg()
        CooItemGroup.reg()
        CooModParticles.reg()
        initEntityTypes()
        CooModParticles.particleTypes.forEach {
            Registry.register(it.type, it.id, it.get())
        }
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(CooShaderReloadListener)

    }

    private fun initEvents() {
        ServerTickEvents.START_SERVER_TICK.register { server ->
            CooEventBus.call(
                ServerPreTickEvent(server)
            )
            CooParticlesAPI.tickServer(server)
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            CooEventBus.call(
                ServerPostTickEvent(server)
            )
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            CooParticlesAPI.onServerStart(server)
            CooAPIScanner.scan()
            // 注册？
            CooParticlesConstants.logger.info("Server Started Test")
            CooParticlesAPI.loadScannerPackages()
        }

        UseBlockCallback.EVENT.register { player, level, hand, result ->
            val event = EntityPrePlaceBlockEvent(player, level, result.blockPos, result.direction)
            return@register if (CooEventBus.call(event).isCancelled) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        ServerPlayerEvents.ALLOW_DEATH.register { player, source, damage ->
            !CooEventBus.call(ServerPlayerDeathEvent(player, player.level(), source))
                .isCancelled
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { old, new, alive ->
            CooEventBus.call(
                ServerPlayerRespawnEvent(new, new.level())
            )
        }

    }

    private fun initEntityTypes() {
        CooModEntityTypes.types.forEach {
            Registry.register(BuiltInRegistries.ENTITY_TYPE, it.id, it.get())
        }
    }

}
