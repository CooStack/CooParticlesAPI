package cn.coostack.cooparticlesapi.annotations.codec

interface CommonStreamCodec<T> {
    fun decode(buf: Any): T
    fun encode(buf: Any, value: T)
    companion object {
        fun <T> of(dec: (Any) -> T, enc: (Any, T) -> Unit): CommonStreamCodec<T> =
            object : CommonStreamCodec<T> {
                override fun decode(buf: Any): T = dec(buf)
                override fun encode(buf: Any, value: T) = enc(buf, value)
            }
    }
}
