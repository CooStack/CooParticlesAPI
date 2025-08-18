package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.core.component.DataComponentType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

class CommonDeferredRecipeSerializer<T : Recipe<*>>(
    val id: ResourceLocation,
    val supplier: Supplier<RecipeSerializer<T>>
) {

    private var value: RecipeSerializer<T>? = null

    fun get(): RecipeSerializer<T> {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}