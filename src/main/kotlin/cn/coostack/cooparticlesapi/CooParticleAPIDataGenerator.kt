package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.datagen.ItemModelProvider
import cn.coostack.cooparticlesapi.datagen.LanguageProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

object CooParticleAPIDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        if (fabricDataGenerator.modId != CooParticleAPI.MOD_ID) {
            return
        }
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::LanguageProvider)
        pack.addProvider(::ItemModelProvider)
    }
}