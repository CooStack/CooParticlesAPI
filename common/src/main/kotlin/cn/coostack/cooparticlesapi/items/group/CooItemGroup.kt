package cn.coostack.cooparticlesapi.items.group

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.items.CooItems
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CooItemGroup {

    val API_GROUP = CooParticlesServices.COO_REGISTRY
        .register(
            CommonDeferredRegistry(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "coo_group")
            ) {
                CreativeModeTab.Builder(null, -1)
                    .title(Component.translatable("item.coo_group"))
                    .icon { ItemStack(Items.BOW) }
                    .displayItems { _, entries ->
                        CooItems.items.forEach {
                            val item = it.getItem()
                            entries.accept { item }
                        }
                    }
                    .build()
            }
        )


    fun reg() {
        CooParticlesConstants.logger.info("注册物品分组成功")
    }
}