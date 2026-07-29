package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals

class SequencedParticleCompositionScaleTest {
    @Test
    fun `scale updates locations that have not been displayed yet`() {
        val composition = TestSequencedComposition()

        composition.prepareCachedLocations(0.01)
        composition.scale(1.0)

        assertEquals(30.0, composition.cachedY(), 1.0E-9)
    }

    private class TestSequencedComposition :
        AutoSequencedParticleComposition(Vec3.ZERO, null) {
        override fun getParticleSequenced(): SortedMap<CompositionData, RelativeLocation> {
            return TreeMap<CompositionData, RelativeLocation>().apply {
                put(CompositionData(), RelativeLocation(0.0, 30.0, 0.0))
            }
        }

        override fun onDisplay() = Unit

        fun prepareCachedLocations(initialScale: Double) {
            scale(initialScale)
            val locations = getParticleSequenced()
            toggleScale(locations)
            sequencedParticlesData.addAll(locations.toList())
            displayed = true
        }

        fun cachedY(): Double = sequencedParticlesData.single().second.y
    }
}
