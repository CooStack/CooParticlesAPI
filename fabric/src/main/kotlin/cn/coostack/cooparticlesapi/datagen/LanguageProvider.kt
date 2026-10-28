package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.items.CooItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

/** 生成内置调试工具的简体中文翻译。 */
class LanguageProvider(
    dataOutput: FabricPackOutput,
    registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricLanguageProvider(dataOutput, "zh_cn", registryLookup) {

    override fun generateTranslations(
        lookup: HolderLookup.Provider,
        builder: TranslationBuilder
    ) {
        builder.apply {
            add("item.coo_group", "§b粒子测试分组")
            add(CooItems.API_GROUP_TESTING.getItem(), "API有效性测试")
            add(CooItems.SINGLE_TESTING.getItem(), "新API单项测试")
            add(CooItems.testSequencedParticle.getItem(), "§a顺序出现粒子组测试工具")
            add(CooItems.testStyleItem.getItem(), "§a粒子样式测试工具-C-S共用")
            add(CooItems.testTickItem.getItem(), "§aTick 粒子测试工具")
            add(CooItems.TEST_CONTROLLER_ITEM.getItem(), "粒子测试方块")
            add(CooItems.TEST_BLOCK_BINDER.getItem(), "测试方块调试器")
        }
    }
}
