package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticleAPI
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object CooItems {
    val items = ArrayList<Item>()

    val testParticle = register(
        "test_particle",
        TestParticleItem(Item.Settings().maxCount(1))
    )

    val testBarrierItem = register(
        "test_barrier_item",
        TestBarrierItem()
    )

    val testSequencedParticle = register(
        "sequenced_test_item", TestSequencedItem()
    )

    val testStyleItem = register(
        "test_style", TestStyleItem()
    )

    val testTickItem = register(
        "test_tick", TestTickItem()
    )

    fun register(id: String, item: Item): Item {
        val res = Registry.register(
            Registries.ITEM, Identifier.of(CooParticleAPI.MOD_ID, id), item
        )
        items.add(res)
        return res
    }

    fun reg() {
        CooParticleAPI.logger.info("注册物品成功")
    }

}