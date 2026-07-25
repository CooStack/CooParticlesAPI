package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.core.Registry
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class NeoForgeRegistry : CooRegistry {
    private val registers = HashMap<Registry<*>, MutableMap<String, DeferredRegister<*>>>()
    private var eventBus: IEventBus? = null

    @Synchronized
    override fun <T : Any> register(registry: CommonDeferredRegistry<T>): CommonDeferredRegistry<T> {
        val registerer = getOrCreateRegister(registry.type, registry.id.namespace)
        registerEntry(registerer, registry)
        return registry
    }

    @Synchronized
    override fun init(any: Any?) {
        any as IEventBus
        if (eventBus != null) {
            return
        }
        eventBus = any
        registers.values
            .flatMap { it.values }
            .forEach { it.register(any) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getOrCreateRegister(
        registry: Registry<T>,
        namespace: String
    ): DeferredRegister<Any> {
        val registersByNamespace = registers.getOrPut(registry) { HashMap() }
        val registerer = (registersByNamespace[namespace]
            ?: DeferredRegister.create(registry, namespace).also { deferred ->
                registersByNamespace[namespace] = deferred
                eventBus?.let(deferred::register)
            }) as DeferredRegister<Any>
        return registerer
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerEntry(
        registerer: DeferredRegister<*>,
        registry: CommonDeferredRegistry<*>
    ) {
        (registerer as DeferredRegister<Any>)
            .register(registry.id.path, Supplier { registry.get() })
    }
}
