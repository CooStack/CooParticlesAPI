package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.core.component.DataComponentType
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

class CommonDeferredSoundEvent(
    val id: ResourceLocation,
    val supplier: Supplier<SoundEvent>
) {

    private var value: SoundEvent? = null

    fun get(): SoundEvent {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}