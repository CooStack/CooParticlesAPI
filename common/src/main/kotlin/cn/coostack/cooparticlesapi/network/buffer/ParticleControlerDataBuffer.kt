package cn.coostack.cooparticlesapi.network.buffer

import net.minecraft.resources.ResourceLocation


interface ParticleControlerDataBuffer<T> {

    data class Id(val value: ResourceLocation) {
        companion object {
            @JvmStatic
            fun toID(string: String): Id {
                val split = string.split(":")
                if (split.size != 2) {
                    throw IllegalArgumentException("Invalid ID format: $string")
                }
                val namespace = split[0]
                val id = split[1]
                return Id(ResourceLocation.fromNamespaceAndPath(namespace, id))
            }
        }
    }

    var loadedValue: T?
    fun encode(): ByteArray?

    fun encode(value: T): ByteArray

    fun decode(buf: ByteArray): T

    fun getBufferID(): Id

}