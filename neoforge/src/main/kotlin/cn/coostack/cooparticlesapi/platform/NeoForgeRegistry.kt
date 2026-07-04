package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.Registry
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class NeoForgeRegistry : CooRegistry {
    private val registers = HashMap<Registry<*>, DeferredRegister<*>>()
    private val needToRegister = ArrayList<CommonDeferredRegistry<*>>()
    override fun <T : Any> register(registry: CommonDeferredRegistry<T>): CommonDeferredRegistry<T> {
        if (!registers.containsKey(registry.type)) {
            val new = DeferredRegister.create(registry.type, registry.id.namespace)
            registers[registry.type] = new
        }
        needToRegister += registry
        return registry
    }

    override fun init(any: Any?) {
        any as IEventBus
        registers.forEach {
            it.value.register(any)
        }
        needToRegister.forEach { registry ->
            val registerer = if (registers.containsKey(registry.type)) {
                registers[registry.type]!!
            } else {
                val new = DeferredRegister.create(registry.type, registry.id.namespace)
                registers[registry.type] = new
                new
            } as DeferredRegister<Any>
            registerer.register(registry.id.path, Supplier { registry.get() as Any })
        }
    }
}
