package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SequencedParticleCompositionScaleTest {
    @Test
    fun `scale updates locations that have not been displayed yet`() {
        val composition = TestSequencedComposition()

        composition.prepareCachedLocations(0.01)
        composition.scale(1.0)

        assertEquals(30.0, composition.cachedY(), 0.000000001)
    }

    @Test
    fun `zero scale can restore cached particle direction`() {
        val composition = TestSequencedComposition()
        composition.prepareCachedLocations(1.0)

        composition.scale(0.0)
        assertEquals(0.0, composition.cachedY(), 0.000000001)

        composition.scale(2.0)
        assertEquals(60.0, composition.cachedY(), 0.000000001)
    }

    @Test
    fun `zero scale restores non-axis particle direction`() {
        val composition = TestSequencedComposition()
        composition.prepareCachedLocation(RelativeLocation(2.0, 3.0, 4.0))
        val before = composition.cachedLocation()

        composition.scale(0.0)
        composition.scale(2.0)
        val restored = composition.cachedLocation()

        assertEquals(before.x * 2.0, restored.x, 0.000000001)
        assertEquals(before.y * 2.0, restored.y, 0.000000001)
        assertEquals(before.z * 2.0, restored.z, 0.000000001)
    }

    @Test
    fun `full composition update copies scale state`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val local = TestSequencedComposition()
        val remote = TestSequencedComposition()
        remote.scale(3.0)

        local.update(remote)

        assertEquals(3.0, local.scale, 0.000000001)
    }

    @Test
    fun `client full update restores displayed sequence nodes`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val local = TestSequencedComposition()
        val remote = TestSequencedComposition()
        local.prepareClientSequenceState(2)
        remote.prepareSequenceState(2)
        remote.setParticleStatus(1, true)

        local.update(remote)

        assertTrue(local.displayEntryCalls > 0)
        assertTrue(local.isParticleDisplayed(1))
        assertEquals(1, local.displayedParticleCount)
    }

    @Test
    fun `successful sequence mutations mark full network state dirty`() {
        val composition = TestSequencedComposition()
        composition.prepareSequenceState(2)
        composition.consumeNetworkFullDirty()

        composition.addSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertTrue(composition.isParticleDisplayed(0))
        assertEquals(1, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.addSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertTrue(composition.isParticleDisplayed(1))
        assertEquals(2, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.removeSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertFalse(composition.isParticleDisplayed(1))
        assertEquals(1, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.addSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertTrue(composition.isParticleDisplayed(1))
        assertEquals(2, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.addSingle()
        assertFalse(composition.hasNetworkFullDirty())

        composition.removeSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertFalse(composition.isParticleDisplayed(1))
        assertEquals(1, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.removeSingle()
        assertTrue(composition.hasNetworkFullDirty())
        assertFalse(composition.isParticleDisplayed(0))
        assertEquals(0, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.removeSingle()
        assertFalse(composition.hasNetworkFullDirty())

        composition.setParticleStatus(0, true)
        assertTrue(composition.hasNetworkFullDirty())
        assertEquals(1, composition.displayedParticleCount)
        assertFalse(composition.isParticleDisplayed(2))

        composition.consumeNetworkFullDirty()
        composition.setParticleStatus(0, true)
        assertFalse(composition.hasNetworkFullDirty())

        composition.resetAll()
        assertTrue(composition.hasNetworkFullDirty())
        assertFalse(composition.isParticleDisplayed(0))
        assertEquals(0, composition.displayedParticleCount)

        composition.consumeNetworkFullDirty()
        composition.resetAll()
        assertFalse(composition.hasNetworkFullDirty())
    }

    private class TestSequencedComposition :
        AutoSequencedParticleComposition(Vec3.ZERO, null) {
        var displayEntryCalls = 0
            private set

        override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
            return TreeMap<CompositionData, RelativeLocation>().apply {
                put(CompositionData(), RelativeLocation(0.0, 30.0, 0.0))
            }
        }

        override fun onDisplay() = Unit

        override fun displayEntry(data: CompositionData, pos: RelativeLocation) {
            displayEntryCalls++
        }

        fun prepareSequenceState(particleCount: Int) {
            count = particleCount
            index.setMemoValue(LongArray((particleCount + 63) / 64))
        }

        fun prepareClientSequenceState(particleCount: Int) {
            prepareSequenceState(particleCount)
            client = true
            sequencedParticlesData.clear()
            repeat(particleCount) { particleIndex ->
                sequencedParticlesData += CompositionData() to RelativeLocation(0.0, particleIndex + 1.0, 0.0)
            }
        }

        fun prepareCachedLocations(initialScale: Double) {
            scale(initialScale)
            val locations = getParticleSequenced()
            toggleScale(locations)
            sequencedParticlesData.addAll(locations.toList())
            displayed = true
        }

        fun prepareCachedLocation(location: RelativeLocation) {
            val locations = TreeMap<CompositionData, RelativeLocation>().apply {
                put(CompositionData(), location)
            }
            toggleScale(locations)
            sequencedParticlesData.addAll(locations.toList())
            displayed = true
        }

        fun cachedLocation(): RelativeLocation = sequencedParticlesData.single().second.clone()

        fun cachedY(): Double = sequencedParticlesData.single().second.y
    }
}
