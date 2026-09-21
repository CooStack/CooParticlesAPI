package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.items.CooItemForge
import cn.coostack.cooparticlesapi.items.group.CooItemGroup
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(CooParticlesConstants.MOD_ID)
object CooParticlesAPIForge {
    init {
        MOD_BUS.addListener(::onCommon)
        MOD_BUS.addListener(::onRegistryRegister)
        CooParticlesConstants.logger.info("Listener registered on CooParticlesForge Initialize")
        CooParticlesAPI.init()
        CooItemForge.reg(MOD_BUS)
        CooItemGroup.reg()
        CooParticlesServices.COO_REGISTRY.init(MOD_BUS)
        setupNetwork()
    }

    fun setupNetwork() {
        cn.coostack.cooparticlesapi.platform.ForgeNetworkChannel.registerEnvelopeS2C { envelope ->
            CooClientPacketManager.handleS2C(envelope)
        }
        cn.coostack.cooparticlesapi.platform.ForgeNetworkChannel.registerEnvelopeC2S { envelope, sender ->
            CooServerPacketManager.handleC2S(envelope, sender)
        }
    }

    fun onCommon(event: FMLCommonSetupEvent) {
        CooParticlesConstants.logger.info("所有模组加载完毕 CooParticlesAPI->Called test")
        CooParticlesAPI.loadScannerPackages()
        CooAPIScanner.neoLoaded()
    }

    @SubscribeEvent
    fun onRegistryRegister(event: RegisterEvent) {
        event.register(BuiltInRegistries.PARTICLE_TYPE.key()) {
            CooModParticles.particleTypes.forEach { type ->
                it.register(type.id, type.get())
            }
        }
        event.register(BuiltInRegistries.ENTITY_TYPE.key()) {
            CooModEntityTypes.types.forEach { type ->
                it.register(type.id, type.get())
            }
        }
    }
}
