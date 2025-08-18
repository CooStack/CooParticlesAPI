package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.Registry

class FabricRegistry : CooRegistry {
    override fun <T> register(registry: CommonDeferredRegistry<T>): CommonDeferredRegistry<T> {
        Registry.register(registry.type, registry.id, registry.supplier.get()!!)
        return registry
    }

    override fun init(any: Any?) {
    }

}