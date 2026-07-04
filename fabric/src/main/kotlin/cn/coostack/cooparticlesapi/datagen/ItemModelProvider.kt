package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.items.CooItems
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider
import net.minecraft.data.models.BlockModelGenerators
import net.minecraft.data.models.ItemModelGenerators
import net.minecraft.data.models.model.ModelTemplates

class ItemModelProvider(output: FabricDataOutput?) : FabricModelProvider(output) {


    override fun generateBlockStateModels(gen: BlockModelGenerators) {
    }

    override fun generateItemModels(gen: ItemModelGenerators) {
        gen.apply {
            this.generateFlatItem(CooItems.API_GROUP_TESTING.getItem(), ModelTemplates.FLAT_ITEM)
            this.generateFlatItem(CooItems.SINGLE_TESTING.getItem(), ModelTemplates.FLAT_HANDHELD_ITEM)
            this.generateFlatItem(CooItems.testSequencedParticle.getItem(), ModelTemplates.FLAT_HANDHELD_ITEM)
            this.generateFlatItem(CooItems.testStyleItem.getItem(), ModelTemplates.FLAT_HANDHELD_ITEM)
            this.generateFlatItem(CooItems.testTickItem.getItem(), ModelTemplates.FLAT_HANDHELD_ITEM)
        }
    }
}
