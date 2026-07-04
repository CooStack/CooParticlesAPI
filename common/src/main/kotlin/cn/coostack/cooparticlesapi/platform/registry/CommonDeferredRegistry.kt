package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

class CommonDeferredRegistry<T : Any>(val type: Registry<T>, val id: ResourceLocation, val supplier: Supplier<T>) {
    var value: T? = null
    fun get(): T {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }
}
