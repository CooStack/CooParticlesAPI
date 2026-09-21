package cn.coostack.cooparticlesapi.items

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.world.item.Item
import net.minecraftforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.forge.KotlinForgeForge

object CooItemForge {
    @JvmStatic
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(KotlinForgeForge.MOD_EVENT_BUS, CooParticlesConstants.MOD_ID)

    @JvmStatic
    fun reg(bus: Any) {
        ITEMS.register(bus as net.minecraftforge.eventbus.api.IEventBus)
        CooItems.getRegisterItems()
        CooItems.itemsWithID.forEach { (key, value) ->
            CooParticlesConstants.logger.info("register item :${key.path}")
            ITEMS.register(key.path) { value.getItem().get() }
        }
    }
}
