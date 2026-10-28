package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.items.CooItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.model.ModelTemplates

/** 生成调试物品的 26.3 客户端物品模型及对应资产定义。 */
class ItemModelProvider(output: FabricPackOutput) : FabricModelProvider(output) {


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
