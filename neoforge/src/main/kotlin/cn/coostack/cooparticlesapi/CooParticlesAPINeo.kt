package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.items.CooItemNeoForge
import cn.coostack.cooparticlesapi.items.group.CooItemGroup
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(CooParticlesConstants.MOD_ID)
object CooParticlesAPINeo {
    init {
        MOD_BUS.addListener(::onCommon)
        MOD_BUS.addListener(::onRegistryRegister)
        CooParticlesConstants.logger.info("Listener registered on CooParticlesNeo Initialize")
        CooParticlesAPI.init()
        CooItemNeoForge.reg(MOD_BUS)
        CooParticlesServices.COO_REGISTRY.init(MOD_BUS)
    }


    fun onCommon(event: FMLCommonSetupEvent) {
        CooParticlesConstants.logger.info("on Common invoked")
    }

    fun onRegistryRegister(event: RegisterEvent) {
        CooModParticles.reg()
        event.register(BuiltInRegistries.CREATIVE_MODE_TAB.key()) {
            it.register(CooItemGroup.API_GROUP.id, CooItemGroup.API_GROUP.get()!!)
        }
        event.register(BuiltInRegistries.PARTICLE_TYPE.key()) {
            CooModParticles.particleTypes.forEach { type ->
                it.register(type.id, type.get())
            }
        }
    }
}

