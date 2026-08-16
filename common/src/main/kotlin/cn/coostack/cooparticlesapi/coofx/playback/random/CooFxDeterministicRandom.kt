package cn.coostack.cooparticlesapi.coofx.playback.random

/**
 * 按 CooFX 协议实现 SplitMix64。所有运算使用无符号 64 位溢出语义，输出序列不依赖 JVM 随机实现。
 */
class CooFxDeterministicRandom(seed: ULong) {
    private var state = seed

    fun nextULong(): ULong {
        state += 0x9e3779b97f4a7c15uL
        return mix64(state)
    }

    fun nextFloat(): Float {
        val highBits = (nextULong() shr 40).toUInt()
        return highBits.toFloat() / 16777216F
    }

    companion object {
        /** SplitMix64 的无状态终混合函数，也是 CooFX 固定 seed 派生链的基础算法。 */
        fun mix64(input: ULong): ULong {
            var value = input
            value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9uL
            value = (value xor (value shr 27)) * 0x94d049bb133111ebuL
            return value xor (value shr 31)
        }
    }
}
