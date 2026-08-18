package cn.coostack.cooparticlesapi.coofx.playback

import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackInterpolation
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackSampler
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CooFxTrackSamplerTest {
    @Test
    fun `step holds previous value and clamps endpoints`() {
        val track = CooFxTrack(floatArrayOf(1F, 2F), floatArrayOf(10F, 20F), 1, CooFxTrackInterpolation.STEP)
        assertContentEquals(floatArrayOf(10F), CooFxTrackSampler.sample(track, 0F))
        assertContentEquals(floatArrayOf(10F), CooFxTrackSampler.sample(track, 1.999F))
        assertContentEquals(floatArrayOf(20F), CooFxTrackSampler.sample(track, 2F))
        assertContentEquals(floatArrayOf(20F), CooFxTrackSampler.sample(track, 3F))
    }

    @Test
    fun `linear interpolates vector components`() {
        val track = CooFxTrack(floatArrayOf(0F, 2F), floatArrayOf(0F, 2F, 4F, 2F, 4F, 8F), 3, CooFxTrackInterpolation.LINEAR)
        assertContentEquals(floatArrayOf(1F, 3F, 6F), CooFxTrackSampler.sample(track, 1F))
    }

    @Test
    fun `cubic spline scales tangents by segment seconds`() {
        val track = CooFxTrack(
            floatArrayOf(0F, 2F),
            floatArrayOf(0F, 0F, 2F, 0F, 2F, 0F),
            1,
            CooFxTrackInterpolation.CUBICSPLINE
        )
        assertEquals(1.5F, CooFxTrackSampler.sample(track, 1F)[0], 1.0E-6F)
    }

    @Test
    fun `quaternion linear chooses shortest path and normalizes`() {
        val track = CooFxTrack(
            floatArrayOf(0F, 1F),
            floatArrayOf(0F, 0F, 0F, 1F, 0F, 0F, 0F, -1F),
            4,
            CooFxTrackInterpolation.LINEAR,
            quaternion = true
        )
        val sampled = CooFxTrackSampler.sample(track, 0.5F)
        assertContentEquals(floatArrayOf(0F, 0F, 0F, 1F), sampled)
        assertEquals(1F, sqrt(sampled.sumOf { value -> (value * value).toDouble() }).toFloat(), 1.0E-6F)
    }
}
