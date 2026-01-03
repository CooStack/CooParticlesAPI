package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredItem
import cn.coostack.cooparticlesapi.items.CooItems
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper

class CooItemModelProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    ItemModelProvider(output, CooParticlesConstants.MOD_ID, existingFileHelper) {
    override fun registerModels() {
        registerWith(CooItems.testTickItem)
        registerWith(CooItems.testStyleItem)
        registerWith(CooItems.SINGLE_TESTING)
        registerWith(CooItems.API_GROUP_TESTING)
        registerWith(CooItems.testSequencedParticle)
        CooParticlesConstants.logger.info("物品模型注册.............. Item Model generated")
    }

    private fun registerWith(item: CommonDeferredItem) {
        val name = item.id.toString()
        val path = item.id.path
        withExistingParent(name, mcLoc("item/handheld"))
            .texture("layer0", modLoc("item/$path"))

    }
}