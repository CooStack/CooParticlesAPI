package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Math3DUtilBezierCurveTest {
    @Test
    fun `spatial bezier curve includes endpoints and bends in z`() {
        val start = RelativeLocation(1.0, 2.0, 3.0)
        val end = RelativeLocation(4.0, 5.0, 6.0)
        val startHandle = RelativeLocation(1.0, 0.0, 0.0)
        val endHandle = RelativeLocation(-1.0, 0.0, 2.0)

        val points = Math3DUtil.generateBezierCurve(start, end, startHandle, endHandle, 3)

        assertEquals(3, points.size)
        assertEquals(start, points.first())
        assertEquals(end, points.last())
        assertEquals(2.5, points[1].x, 1.0E-9)
        assertEquals(3.5, points[1].y, 1.0E-9)
        assertEquals(5.25, points[1].z, 1.0E-9)
    }

    @Test
    fun `legacy bezier overload stays on xy plane`() {
        val target = RelativeLocation(4.0, 5.0, 7.0)
        val startHandle = RelativeLocation(1.0, 2.0, 3.0)
        val endHandle = RelativeLocation(-1.0, -2.0, -3.0)

        val points = Math3DUtil.generateBezierCurve(target, startHandle, endHandle, 4)

        assertEquals(RelativeLocation(0.0, 0.0, 0.0), points.first())
        assertEquals(RelativeLocation(4.0, 5.0, 0.0), points.last())
        assertTrue(points.all { it.z == 0.0 })
    }

    @Test
    fun `points builder spatial bezier matches math util`() {
        val start = RelativeLocation(-2.0, 0.5, 1.0)
        val end = RelativeLocation(3.0, 4.0, -1.5)
        val startHandle = RelativeLocation(0.0, 2.0, 1.0)
        val endHandle = RelativeLocation(-1.0, -1.0, 2.5)

        val expected = Math3DUtil.generateBezierCurve(start, end, startHandle, endHandle, 8)
        val actual = PointsBuilder.of(RelativeLocation.yAxis())
            .addBezierCurve(start, end, startHandle, endHandle, 8)
            .create()

        assertContentEquals(expected, actual)
    }
}
