package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

class CommonDeferredBlock(
    val id: ResourceLocation,
    val supplier: Supplier<Block>
) {

    private var value: Block? = null

    fun get(): Block {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}