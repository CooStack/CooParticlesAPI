package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.core.component.DataComponentType
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

class CommonDeferredComponentType<T : Any>(
    val id: Identifier,
    val supplier: Supplier<DataComponentType<T>>
) {

    private var value: DataComponentType<T>? = null

    fun get(): DataComponentType<T> {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}
