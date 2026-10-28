package cn.coostack.cooparticlesapi.data.cache

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.Identifier

class CacheKey<T>(val targetType: Class<T>, val id: Identifier) {
    companion object {
        inline fun <reified T> ofCooParticle(id: String): CacheKey<T> {
            return CacheKey(
                T::class.java, Identifier.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID, id
                )
            )
        }

        inline fun <reified T> of(id: Identifier): CacheKey<T> {
            return CacheKey(T::class.java, id)
        }
    }
}