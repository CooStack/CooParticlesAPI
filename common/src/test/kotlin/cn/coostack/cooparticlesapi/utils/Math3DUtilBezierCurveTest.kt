package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
        assertTrue(points[1].z > start.z)
        assertNotEquals(RelativeLocation(2.5, 3.5, 5.25), points[1])
    }

    @Test
    fun `smooth bezier curve keeps parameter based sampling`() {
        val start = RelativeLocation(1.0, 2.0, 3.0)
        val end = RelativeLocation(4.0, 5.0, 6.0)
        val startHandle = RelativeLocation(1.0, 0.0, 0.0)
        val endHandle = RelativeLocation(-1.0, 0.0, 2.0)

        val points = Math3DUtil.generateSmoothBezierCurve(start, end, startHandle, endHandle, 3)

        assertEquals(RelativeLocation(2.5, 3.5, 5.25), points[1])
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

    @Test
    fun `spatial bezier accepts multiple control points`() {
        val controls = listOf(
            BezierNode(
                RelativeLocation(0.0, 0.0, 0.0),
                startHandle = RelativeLocation(2.0, 4.0, 1.0)
            ),
            BezierNode(
                RelativeLocation(6.0, -1.0, 3.0),
                endHandle = RelativeLocation(-2.0, 3.0, 0.0),
                startHandle = RelativeLocation(2.0, 2.0, -1.0)
            ),
            BezierNode(
                RelativeLocation(8.0, 0.0, 0.0),
                endHandle = RelativeLocation(-2.0, 1.0, 0.0)
            )
        )

        val points = Math3DUtil.generateBezierCurve(controls, 9)
        val builderPoints = PointsBuilder().addBezierCurve(controls, 9).create()

        assertEquals(9, points.size)
        assertEquals(controls.first().point, points.first())
        assertEquals(controls.last().point, points.last())
        assertTrue(points.any { it.y > 1.0 })
        assertContentEquals(points, builderPoints)
    }

    @Test
    fun `points builder accepts bezier node dsl`() {
        val controls = listOf(
            BezierNode(
                RelativeLocation(0.25, 0.0, -2.125),
                RelativeLocation(-0.75, 0.0, 1.75)
            ),
            BezierNode(
                RelativeLocation(-2.0, 0.0, 3.125),
                RelativeLocation(2.208333, 0.0, -0.791667)
            ),
            BezierNode(
                RelativeLocation(4.625, 0.0, 0.75),
                RelativeLocation(-1.0, 0.0, -1.25)
            )
        )

        val expected = PointsBuilder().addBezierCurve(controls, 100).create()
        val actual = PointsBuilder().addBezierCurve(100) {
            addNode(
                RelativeLocation(0.25, 0.0, -2.125),
                RelativeLocation(-0.75, 0.0, 1.75)
            )
            addNode(
                RelativeLocation(-2.0, 0.0, 3.125),
                RelativeLocation(2.208333, 0.0, -0.791667)
            )
            addNode(
                RelativeLocation(4.625, 0.0, 0.75),
                RelativeLocation(-1.0, 0.0, -1.25)
            )
        }.create()

        assertContentEquals(expected, actual)
    }

    @Test
    fun `short spatial bezier keeps nonzero distances`() {
        val points = Math3DUtil.generateBezierCurve(
            RelativeLocation(0.0, 0.0, 0.0),
            RelativeLocation(1.0E-9, 0.0, 0.0),
            RelativeLocation(),
            RelativeLocation(),
            3
        )

        assertEquals(0.0, points[0].x, 1.0E-18)
        assertEquals(5.0E-10, points[1].x, 1.0E-18)
        assertEquals(1.0E-9, points[2].x, 1.0E-18)
    }

    @Test
    fun `adaptive spatial bezier keeps linear segments exact`() {
        val nodes = listOf(
            BezierNode(RelativeLocation(0.0, 0.0, 0.0)),
            BezierNode(RelativeLocation(4.0, 0.0, 0.0)),
            BezierNode(RelativeLocation(10.0, 0.0, 0.0))
        )

        val points = Math3DUtil.generateEquidistantBezierCurve(nodes, 101)

        points.forEachIndexed { index, point ->
            assertEquals(index / 10.0, point.x, 1.0E-12)
            assertEquals(0.0, point.y, 1.0E-12)
            assertEquals(0.0, point.z, 1.0E-12)
        }
    }

    @Test
    fun `adaptive spatial bezier preserves endpoint count shortcuts`() {
        val nodes = listOf(
            BezierNode(RelativeLocation(1.0, 2.0, 3.0)),
            BezierNode(RelativeLocation(4.0, 5.0, 6.0))
        )

        assertContentEquals(
            listOf(nodes.last().point),
            Math3DUtil.generateEquidistantBezierCurve(nodes, 1)
        )
        assertContentEquals(
            listOf(nodes.first().point, nodes.last().point),
            Math3DUtil.generateEquidistantBezierCurve(nodes, 2)
        )
    }

    @Test
    fun `adaptive spatial bezier tracks a tight loop`() {
        val nodes = listOf(
            BezierNode(
                RelativeLocation(),
                startHandle = RelativeLocation(20.0, 30.0, 10.0)
            ),
            BezierNode(
                RelativeLocation(),
                endHandle = RelativeLocation(-20.0, 30.0, -10.0)
            )
        )
        val count = 101
        val expected = Math3DUtil.sampleByDistance(
            Math3DUtil.generateSmoothBezierCurve(nodes, 16_384),
            count
        )
        val actual = Math3DUtil.generateEquidistantBezierCurve(nodes, count)

        val maxError = actual.indices.maxOf { index -> actual[index].distance(expected[index]) }
        assertTrue(maxError <= 0.01, "adaptive sampling error was $maxError")
    }

    @Test
    fun `adaptive spatial bezier keeps a long shallow bend`() {
        val nodes = listOf(
            BezierNode(
                RelativeLocation(0.0, 0.0, 0.0),
                startHandle = RelativeLocation(250_000_000.0, 1_000.0, 0.0)
            ),
            BezierNode(
                RelativeLocation(1_000_000_000.0, 0.0, 0.0),
                endHandle = RelativeLocation(-250_000_000.0, -1_000.0, 0.0)
            )
        )
        val points = Math3DUtil.generateEquidistantBezierCurve(nodes, 101)

        assertTrue(points.any { it.y > 100.0 })
        assertEquals(0.0, points.first().y, 1.0E-12)
        assertEquals(0.0, points.last().y, 1.0E-12)
    }
}
