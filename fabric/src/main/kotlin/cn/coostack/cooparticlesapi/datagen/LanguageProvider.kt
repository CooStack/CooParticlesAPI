package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.items.CooItems
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

class LanguageProvider(
    dataOutput: FabricDataOutput?,
    registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricLanguageProvider(dataOutput, "zh_cn", registryLookup) {

    override fun generateTranslations(
        lookup: HolderLookup.Provider?,
        builder: TranslationBuilder
    ) {
        builder.apply {
            add("item.coo_group", "§b粒子测试分组")
            add(CooItems.API_GROUP_TESTING.getItem(), "API有效性测试")
            add(CooItems.SINGLE_TESTING.getItem(), "新API单项测试")
            add(CooItems.testSequencedParticle.getItem(), "§a顺序出现粒子组测试工具")
            add(CooItems.testStyleItem.getItem(), "§a粒子样式测试工具-C-S共用")
        }
    }
}