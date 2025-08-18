package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.items.CooItemFabric
import cn.coostack.cooparticlesapi.items.group.CooItemGroup
import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleEmittersS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleGroupS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketParticleStyleS2C
import cn.coostack.cooparticlesapi.network.packet.PacketRenderEntityS2C
import cn.coostack.cooparticlesapi.particles.CooModParticles
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.Registry
import net.minecraft.server.MinecraftServer

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
    }

    private fun initRegistries() {
        CooItemFabric.reg()
        CooItemGroup.reg()
        CooModParticles.reg()
        CooModParticles.particleTypes.forEach {
            Registry.register(it.type, it.id, it.get())
        }
    }

    private fun initEvents() {
        ServerTickEvents.START_SERVER_TICK.register { server ->
            CooParticlesAPI.tickServer(server)
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            CooParticlesAPI.onServerStart(server)
        }
    }

}