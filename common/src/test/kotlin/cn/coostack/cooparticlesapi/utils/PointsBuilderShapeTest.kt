package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointsBuilderShapeTest {
    @Test
    fun `ball surface uses total count`() {
        val points = PointsBuilder().addBallSurface(2.0, 37).create()

        assertEquals(37, points.size)
        assertTrue(points.all { abs(sqrt(it.x * it.x + it.y * it.y + it.z * it.z) - 2.0) < 1.0e-9 })
    }

    @Test
    fun `ball solid stays inside radius`() {
        val points = PointsBuilder().addBallSoid(2.0, 41).create()

        assertEquals(41, points.size)
        assertTrue(points.all { it.x * it.x + it.y * it.y + it.z * it.z <= 4.0 + 1.0e-9 })
    }

    @Test
    fun `cube surface and volume respect dimensions`() {
        val surface = PointsBuilder().addCubeSurface(4.0, 2.0, 6.0, 60).create()
        val volume = PointsBuilder().addCubeSoid(4.0, 2.0, 6.0, 60).create()

        assertEquals(60, surface.size)
        assertEquals(60, volume.size)
        assertTrue(surface.all { it.x in -2.0..2.0 && it.y in -1.0..1.0 && it.z in -3.0..3.0 })
        assertTrue(surface.all {
            abs(abs(it.x) - 2.0) < 1.0e-9 || abs(abs(it.y) - 1.0) < 1.0e-9 || abs(abs(it.z) - 3.0) < 1.0e-9
        })
        assertTrue(volume.all { it.x in -2.0..2.0 && it.y in -1.0..1.0 && it.z in -3.0..3.0 })
    }

    @Test
    fun `cube wireframe uses edges and offset`() {
        val offset = RelativeLocation(3.0, -2.0, 5.0)
        val points = PointsBuilder().addCubeWireframe(offset, 2.0, 4.0, 6.0, 48).create()

        assertEquals(48, points.size)
        assertTrue(points.all {
            val x = abs(abs(it.x - offset.x) - 1.0) < 1.0e-9
            val y = abs(abs(it.y - offset.y) - 2.0) < 1.0e-9
            val z = abs(abs(it.z - offset.z) - 3.0) < 1.0e-9
            (x && y) || (x && z) || (y && z)
        })
    }
}
