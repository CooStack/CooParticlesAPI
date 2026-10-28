package cn.coostack.cooparticlesapi.platform.registry

import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import java.util.function.Supplier

class CommonDeferredRegistry<T : Any>(val type: Registry<T>, val id: Identifier, val supplier: Supplier<T>) {
    var value: T? = null
    fun get(): T {
        if (value == null) {
            value = supplier.get()
        }
        return value!!
    }
}
