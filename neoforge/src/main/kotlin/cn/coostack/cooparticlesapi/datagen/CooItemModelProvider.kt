package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.items.CooItems
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ModelProvider
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.core.Holder
import net.minecraft.data.PackOutput
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import java.util.stream.Stream

/** 使用原版 26.3 模型生成器，同时输出模型和客户端物品定义。 */
class CooItemModelProvider(output: PackOutput) : ModelProvider(output, CooParticlesConstants.MOD_ID) {
    private fun generatedItems() = listOf(
        CooItems.testTickItem, CooItems.testStyleItem, CooItems.SINGLE_TESTING,
        CooItems.API_GROUP_TESTING, CooItems.testSequencedParticle
    )

    override fun registerModels(blockModels: BlockModelGenerators, itemModels: ItemModelGenerators) {
        generatedItems().forEach { item ->
            itemModels.generateFlatItem(item.getItem(), ModelTemplates.FLAT_HANDHELD_ITEM)
        }
        CooParticlesConstants.logger.info("物品模型注册.............. Item Model generated")
    }

    override fun getKnownBlocks(): Stream<out Holder<Block>> = Stream.empty()
    override fun getKnownItems(): Stream<out Holder<Item>> =
        generatedItems().stream().map { it.getItem().builtInRegistryHolder() }
}
