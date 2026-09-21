package cn.coostack.cooparticlesapi.network.packet.api

interface CommonCodec<T> {
    fun encode(buf: PacketByteBuf, value: T)
    fun decode(buf: PacketByteBuf): T
    companion object {
        fun <T> of(enc: (PacketByteBuf, T) -> Unit, dec: (PacketByteBuf) -> T): CommonCodec<T> =
            object : CommonCodec<T> {
                override fun encode(buf: PacketByteBuf, value: T) = enc(buf, value)
                override fun decode(buf: PacketByteBuf): T = dec(buf)
            }
    }
}
