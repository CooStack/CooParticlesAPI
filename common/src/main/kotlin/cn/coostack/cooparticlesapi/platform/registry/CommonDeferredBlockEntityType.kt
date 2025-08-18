package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

/**
 * 使用此类须知
 *
 * 1. 由于这个类需要输入一个 block实例
 * 而傻逼的neoforge使用的是延迟注册的方案
 * 所以 这个类的注册 实例创建 一定要在block注册之后!!!
 */
class CommonDeferredBlockEntityType<T : BlockEntity>(
    val id: ResourceLocation,
    val supplier: Supplier<BlockEntityType<T>>
) {

    private var value: BlockEntityType<T>? = null

    fun get(): BlockEntityType<T> {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }

}