package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.blocks.CooBlocks
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredItem
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import java.util.function.Supplier


/** 保存公共测试物品的延迟注册句柄，由各平台在注册阶段创建物品。 */
object CooItems {
    val items = ArrayList<CommonDeferredItem>()
    val itemsWithID = HashMap<Identifier, CommonDeferredItem>()
    var API_GROUP_TESTING = registerConfigured(
        "api_group_testing"
    ) { APIGroupTestingItem(it.stacksTo(1)) }

    var SINGLE_TESTING = register(SingleTesting.ID) {
        SingleTesting()
    }

    val testSequencedParticle = register(
        TestSequencedItem.ID
    ) {
        TestSequencedItem()
    }

    val testStyleItem = register(TestStyleItem.ID) {
        TestStyleItem()
    }

    val testTickItem = register(TestTickItem.ID) {
        TestTickItem()
    }

    val TEST_CONTROLLER_ITEM = registerConfigured("test_controller") {
        BlockItem(CooBlocks.TEST_CONTROLLER.get(), it)
    }

    val TEST_BLOCK_BINDER = registerConfigured("test_block_binder") {
        TestBlockBinderItem(it.stacksTo(1))
    }

    /**
     * 保存已有物品供应器，由平台注册入口按需取得同一个物品实例。
     *
     * @param id 当前模组命名空间中的物品路径
     * @param item 创建物品的供应器，必须在构造前为属性设置与注册一致的 ID
     * @return 平台注册和调用方共享的延迟物品句柄
     */
    fun register(id: String, item: Supplier<Item>): CommonDeferredItem {
        val location = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        val di = CommonDeferredItem(location, item)
        itemsWithID[location] = di
        items.add(di)
        return di
    }

    /** 先设置注册键，再将属性交给内部物品工厂，避免构造期间缺少物品 ID。 */
    private fun registerConfigured(id: String, factory: (Item.Properties) -> Item): CommonDeferredItem {
        val location = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        return register(id) {
            factory(Item.Properties().setId(ResourceKey.create(Registries.ITEM, location)))
        }
    }


    /**
     * 记录公共物品声明已就绪；实际注册由对应平台继续执行。
     *
     * 示例：平台初始化时调用 `CooItems.getRegisterItems()`。
     */
    fun getRegisterItems() {
        CooParticlesConstants.logger.info("创建物品成功 in common")
    }

}
