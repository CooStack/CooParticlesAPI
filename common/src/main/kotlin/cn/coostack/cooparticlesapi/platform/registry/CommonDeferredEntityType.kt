package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

class CommonDeferredEntityType<T : Entity>(
    val id: ResourceLocation,
    val supplier: Supplier<EntityType<T>>
) {

    private var value: EntityType<T>? = null

    fun get(): EntityType<T> {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}