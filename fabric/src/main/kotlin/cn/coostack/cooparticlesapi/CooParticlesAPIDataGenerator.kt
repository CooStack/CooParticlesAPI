package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.datagen.ItemModelProvider
import cn.coostack.cooparticlesapi.datagen.LanguageProvider
import cn.coostack.cooparticlesapi.datagen.TestControllerAssetProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

object CooParticlesAPIDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        if (fabricDataGenerator.modId != CooParticlesConstants.MOD_ID) {
            return
        }
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::LanguageProvider)
        pack.addProvider(::ItemModelProvider)
        pack.addProvider(::TestControllerAssetProvider)
    }
}
