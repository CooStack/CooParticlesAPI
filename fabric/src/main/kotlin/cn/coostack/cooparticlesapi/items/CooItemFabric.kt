package cn.coostack.cooparticlesapi.items

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries

object CooItemFabric {
    fun reg() {
        CooItems.getRegisterItems()
        CooItems.itemsWithID.forEach {
            Registry.register(
                BuiltInRegistries.ITEM, it.key, it.value.getItem()
            )
        }
    }
}