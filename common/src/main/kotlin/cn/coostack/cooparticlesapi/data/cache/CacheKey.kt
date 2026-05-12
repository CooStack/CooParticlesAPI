package cn.coostack.cooparticlesapi.data.cache

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

class CacheKey<T>(val targetType: Class<T>, val id: ResourceLocation) {
    companion object {
        inline fun <reified T> ofCooParticle(id: String): CacheKey<T> {
            return CacheKey(
                T::class.java, ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID, id
                )
            )
        }

        inline fun <reified T> of(id: ResourceLocation): CacheKey<T> {
            return CacheKey(T::class.java, id)
        }
    }
}