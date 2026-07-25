package cn.coostack.cooparticlesapi.network.particle.emitters.command.curve

import kotlin.test.Test
import kotlin.test.assertEquals

class BezierKeyframeFloatCurveTest {
    @Test
    fun `samples target api shape through float curve interface`() {
        val curve: FloatCurve = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(time = 0.0, value = 1.0, outX = 25.0, outY = 0.0, inX = 0.0, inY = 0.0),
                BezierFloatKeyframe(time = 1.0, value = 0.0, outX = 0.0, outY = 0.0, inX = -25.0, inY = 0.0)
            )
        )

        assertEquals(1.0, curve.sample(-1.0), 1.0E-9)
        assertEquals(1.0, curve.sample(0.0), 1.0E-9)
        assertEquals(0.5, curve.sample(0.5), 1.0E-6)
        assertEquals(0.0, curve.sample(1.0), 1.0E-9)
        assertEquals(0.0, curve.sample(2.0), 1.0E-9)
    }

    @Test
    fun `uses y handles in value units`() {
        val curve = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(time = 0.0, value = 0.0, outX = 25.0, outY = 1.0),
                BezierFloatKeyframe(time = 1.0, value = 0.0, inX = -25.0, inY = 1.0)
            )
        )

        assertEquals(0.75, curve.sample(0.5), 1.0E-6)
    }

    @Test
    fun `sorts keyframes and clamps keyframe time`() {
        val curve = BezierKeyframeFloatCurve(
            listOf(
                BezierFloatKeyframe(time = 2.0, value = 10.0, inX = -25.0),
                BezierFloatKeyframe(time = -1.0, value = 0.0, outX = 25.0)
            )
        )

        assertEquals(0.0, curve.sample(0.0), 1.0E-9)
        assertEquals(5.0, curve.sample(0.5), 1.0E-6)
        assertEquals(10.0, curve.sample(1.0), 1.0E-9)
    }
}
