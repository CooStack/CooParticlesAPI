package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.items.CooItemNeoForge
import cn.coostack.cooparticlesapi.items.group.CooItemGroup
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.particles.CooModParticles
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.RegisterEvent
import org.objectweb.asm.Type
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.lang.annotation.ElementType

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
        CooParticlesConstants.logger.info("所有模组加载完毕 CooParticlesAPI->Called test")
        /**
         * 这里直接塞入CooEventBus
         */
        ModList.get().allScanData.forEach {
            it.annotations.groupBy { type ->
                type.clazz.className
            }.forEach { (name, data) ->
                CooAPIScanner.inputScanResult(
                    SimpleClassInfo(
                        name, data.map { anno -> anno.annotationType.className }.toHashSet()
                    )
                )
            }
        }
        CooParticlesAPI.loadScannerPackages()
        CooAPIScanner.neoLoaded()
    }

    fun onRegistryRegister(event: RegisterEvent) {
        event.register(BuiltInRegistries.CREATIVE_MODE_TAB.key()) {
            it.register(CooItemGroup.API_GROUP.id, CooItemGroup.API_GROUP.get()!!)
        }
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

