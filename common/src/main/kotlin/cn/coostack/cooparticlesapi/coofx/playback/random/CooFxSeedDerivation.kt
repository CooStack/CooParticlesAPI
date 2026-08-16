package cn.coostack.cooparticlesapi.coofx.playback.random

/** CooFX 跨 Kotlin/Python 一致的 UTF-8 FNV-1a 64 哈希与固定 seed 派生链。 */
object CooFxSeedDerivation {
    fun fnv1a64(value: String): ULong {
        var hash = 0xcbf29ce484222325uL
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
        }
        return hash
    }

    fun effectSeed(assetSeed: ULong, requestSeed: ULong): ULong {
        return CooFxDeterministicRandom.mix64(assetSeed xor requestSeed)
    }

    fun emitterSeed(effectSeed: ULong, emitterId: String): ULong {
        return CooFxDeterministicRandom.mix64(effectSeed xor fnv1a64(emitterId))
    }

    fun particleSeed(emitterSeed: ULong, emissionOrdinal: ULong): ULong {
        return CooFxDeterministicRandom.mix64(emitterSeed xor emissionOrdinal)
    }

    fun channelSeed(particleSeed: ULong, channelName: String): ULong {
        return CooFxDeterministicRandom.mix64(particleSeed xor fnv1a64(channelName))
    }

    fun parseHexSeed(value: String): ULong {
        require(value.length == 16 && value.all { character -> character in '0'..'9' || character in 'a'..'f' }) {
            "seed 必须是固定十六位小写十六进制字符串"
        }
        return value.toULong(16)
    }

    fun formatHexSeed(value: ULong): String = value.toString(16).padStart(16, '0')
}
