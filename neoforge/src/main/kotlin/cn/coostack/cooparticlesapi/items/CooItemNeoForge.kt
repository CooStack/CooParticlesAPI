package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesAPINeo
import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object CooItemNeoForge {
    @JvmStatic
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(CooParticlesConstants.MOD_ID)

    @JvmStatic
    fun reg(bus: IEventBus) {
        ITEMS.register(bus)
        CooItems.getRegisterItems()
        CooItems.itemsWithID.forEach {
            CooParticlesConstants.logger.info("register item :${it.key.path}")
            ITEMS.register(it.key.path, Supplier { it.value.getItem() })
        }
    }
}