package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.items.CooItems
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.common.data.LanguageProvider

class LangProvider(output: PackOutput) :
    LanguageProvider(output, CooParticlesConstants.MOD_ID, "zh_cn") {
    override fun addTranslations() {
        add("item.coo_group", "§b粒子测试分组")
        add(CooItems.API_GROUP_TESTING.getItem(), "测试粒子物品")
        add(CooItems.SINGLE_TESTING.getItem(), "弹幕测试法杖")
        add(CooItems.testSequencedParticle.getItem(), "§a顺序出现粒子组测试工具")
        add(CooItems.testStyleItem.getItem(), "§a粒子样式测试工具-C-S共用")
        CooParticlesConstants.logger.info("语言注册.............. Language generated")
    }
}