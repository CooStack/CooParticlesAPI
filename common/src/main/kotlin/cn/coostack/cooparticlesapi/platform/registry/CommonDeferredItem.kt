package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import java.util.function.Supplier

class CommonDeferredItem(val id: Identifier, val provider: Supplier<Item>) {
    private var item: Item? = null

    fun getItem(): Item {
        if (item == null) {
            item = provider.get()
        }
        return item!!
    }

}