package cn.coostack.cooparticlesapi.data.cache

import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * # 实现简单实现Entity Data
 * ## Q&A
 * ### Q： 为什么不用Mojang提供的entityData框架？
 * A:
 * - 因为Mojang提供的EntityData框架不允许对抽象类进行Mixin操作
 * - 同时有些时候只是要一个很简单的实体数据暂存的功能因此实现
 */
class EntityCacher {
    private val caches = ConcurrentHashMap<CacheKey<*>, Any>()
    operator fun <T : Any> set(key: CacheKey<T>, value: T) {
        caches[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: CacheKey<T>): T? {
        val res = caches[key]
        return if (res != null) {
            res as T
        } else {
            null
        }
    }


    fun <T : Any> getOrCreate(key: CacheKey<T>, new: Supplier<T>): T {
        return if (!has(key)) {
            val res = new.get()
            this[key] = res
            res
        } else {
            this[key]!!
        }
    }

    fun has(key: CacheKey<*>): Boolean {
        return caches.containsKey(key)
    }

    fun has(key: ResourceLocation): Boolean {
        return caches.keys.any { key == it.id }
    }


    @Suppress("UNCHECKED_CAST")
    fun <T> remove(key: CacheKey<T>): T? {
        val removed = caches.remove(key)
        return if (removed == null) null else removed as? T
    }

    fun removeAllIfId(id: ResourceLocation) {
        caches.keys.removeIf { it.id == id }
    }

}