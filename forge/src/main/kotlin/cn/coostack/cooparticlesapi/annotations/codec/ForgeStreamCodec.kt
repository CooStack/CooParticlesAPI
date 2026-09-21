package cn.coostack.cooparticlesapi.annotations.codec

interface ForgeStreamCodec<T> {
    fun decode(buf: net.minecraft.network.PacketByteBuf): T
    fun encode(buf: net.minecraft.network.PacketByteBuf, value: T)
    companion object {
        fun <T> of(dec: (net.minecraft.network.PacketByteBuf) -> T, enc: (net.minecraft.network.PacketByteBuf, T) -> Unit): ForgeStreamCodec<T> =
            object : ForgeStreamCodec<T> {
                override fun decode(buf: net.minecraft.network.PacketByteBuf): T = dec(buf)
                override fun encode(buf: net.minecraft.network.PacketByteBuf, value: T) = enc(buf, value)
            }
    }
}
