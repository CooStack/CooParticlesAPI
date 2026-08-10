package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import java.util.IdentityHashMap

internal class CooIdentityResourceStore<K : Any, R : Any> {
    private val entries = IdentityHashMap<K, List<R>>()

    @Synchronized
    fun replace(key: K, resources: List<R>, release: (R) -> Unit) {
        entries.remove(key)?.forEach(release)
        if (resources.isNotEmpty()) {
            entries[key] = resources.toList()
        }
    }

    @Synchronized
    fun take(key: K): List<R>? {
        return entries.remove(key)
    }

    @Synchronized
    fun get(key: K): List<R>? {
        return entries[key]?.toList()
    }

    @Synchronized
    fun release(key: K, release: (R) -> Unit) {
        entries.remove(key)?.forEach(release)
    }

    @Synchronized
    fun clear(release: (R) -> Unit) {
        entries.values.flatten().forEach(release)
        entries.clear()
    }

    @Synchronized
    fun size(): Int {
        return entries.size
    }
}
