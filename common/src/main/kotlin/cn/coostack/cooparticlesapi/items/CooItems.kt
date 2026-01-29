package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredItem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import java.util.function.Supplier


object CooItems {
    val items = ArrayList<CommonDeferredItem>()
    val itemsWithID = HashMap<ResourceLocation, CommonDeferredItem>()
    var API_GROUP_TESTING = register(
        "api_group_testing"
    ) { APIGroupTestingItem(Item.Properties().stacksTo(1)) }

    var SINGLE_TESTING = register("single_testing") {
        SingleTesting()
    }

    val testSequencedParticle = register(
        "sequenced_test_item"
    ) {
        TestSequencedItem()
    }

    val testStyleItem = register("test_style") {
        TestStyleItem()
    }

    val testTickItem = register("test_tick") {
        TestTickItem()
    }

    fun register(id: String, item: Supplier<Item>): CommonDeferredItem {
        val location = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        val di = CommonDeferredItem(location, item)
        itemsWithID[location] = di
        items.add(di)
        return di
    }


    /**
     * 交给对应平台处理后, 在重新赋值
     */
    fun getRegisterItems() {
        CooParticlesConstants.logger.info("创建物品成功 in common")
    }

}