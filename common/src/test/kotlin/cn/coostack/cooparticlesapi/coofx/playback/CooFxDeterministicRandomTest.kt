package cn.coostack.cooparticlesapi.coofx.playback

import cn.coostack.cooparticlesapi.coofx.playback.random.CooFxDeterministicRandom
import cn.coostack.cooparticlesapi.coofx.playback.random.CooFxSeedDerivation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CooFxDeterministicRandomTest {
    @Test
    fun `split mix matches public golden vectors`() {
        val random = CooFxDeterministicRandom(0uL)
        assertEquals(0xe220a8397b1dcdafuL, random.nextULong())
        assertEquals(0x6e789e6aa1b965f4uL, random.nextULong())
        assertEquals(0x06c45d188009454fuL, random.nextULong())
    }

    @Test
    fun `fnv hashes utf8 bytes and seed hex round trips`() {
        assertEquals(0xa430d84680aabd0buL, CooFxSeedDerivation.fnv1a64("hello"))
        val seed = CooFxSeedDerivation.parseHexSeed("0123456789abcdef")
        assertEquals("0123456789abcdef", CooFxSeedDerivation.formatHexSeed(seed))
    }

    @Test
    fun `float output is closed open unit interval`() {
        val random = CooFxDeterministicRandom(42uL)
        repeat(1000) {
            assertTrue(random.nextFloat() >= 0F && random.nextFloat() < 1F)
        }
    }
}
