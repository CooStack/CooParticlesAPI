package cn.coostack.cooparticlesapi.network.packet.api

interface PacketByteBuf {
    fun writeUtf(value: String)
    fun writeResourceLocation(value: Any?)
    fun writeInt(value: Int)
    fun writeFloat(value: Float)
    fun writeDouble(value: Double)
    fun writeLong(value: Long)
    fun writeBoolean(value: Boolean)
    fun readUtf(): String
    fun readResourceLocation(): Any?
    fun readInt(): Int
    fun readFloat(): Float
    fun readDouble(): Double
    fun readLong(): Long
    fun readBoolean(): Boolean
}
