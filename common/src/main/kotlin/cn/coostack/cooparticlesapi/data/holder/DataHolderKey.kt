package cn.coostack.cooparticlesapi.data.holder

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

data class DataHolderKey<T>(val targetType: Class<T>, val id: ResourceLocation) {
    companion object {
        inline fun <reified T> ofCooParticle(id: String): DataHolderKey<T> {
            return DataHolderKey(
                T::class.java,
                ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID, id
                )
            )
        }

        inline fun <reified T> of(id: ResourceLocation): DataHolderKey<T> {
            return DataHolderKey(T::class.java, id)
        }

        @Suppress("UNCHECKED_CAST")
        fun ofRaw(type: Class<*>, id: ResourceLocation): DataHolderKey<Any> {
            return DataHolderKey(type as Class<Any>, id)
        }
    }
}
