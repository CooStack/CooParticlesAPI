package cn.coostack.cooparticlesapi.data.holder

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.Identifier

data class DataHolderKey<T>(val targetType: Class<T>, val id: Identifier) {
    companion object {
        inline fun <reified T> ofCooParticle(id: String): DataHolderKey<T> {
            return DataHolderKey(
                T::class.java,
                Identifier.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID, id
                )
            )
        }

        inline fun <reified T> of(id: Identifier): DataHolderKey<T> {
            return DataHolderKey(T::class.java, id)
        }

        @Suppress("UNCHECKED_CAST")
        fun ofRaw(type: Class<*>, id: Identifier): DataHolderKey<Any> {
            return DataHolderKey(type as Class<Any>, id)
        }
    }
}
