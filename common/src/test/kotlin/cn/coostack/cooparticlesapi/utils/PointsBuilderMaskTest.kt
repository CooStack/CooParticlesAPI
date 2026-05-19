package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class PointsBuilderMaskTest {
    @Test
    fun `round xz mask without y range removes points inside radius on any y`() {
        val points = PointsBuilder.of(
            listOf(
                RelativeLocation(1.0, 0.0, 0.0),
                RelativeLocation(1.0, 5.0, 0.0),
                RelativeLocation(2.0, 0.0, 0.0),
            )
        )

        points.clearAsRoundXZMask(RelativeLocation(), 1.8)

        assertEquals(listOf(RelativeLocation(2.0, 0.0, 0.0)), points.create())
    }

    @Test
    fun `round xz mask with y range is centered on origin y`() {
        val points = PointsBuilder.of(
            listOf(
                RelativeLocation(1.0, 10.5, 0.0),
                RelativeLocation(1.0, 12.0, 0.0),
                RelativeLocation(1.0, 8.8, 0.0),
            )
        )

        points.clearAsRoundXZMask(RelativeLocation(0.0, 10.0, 0.0), 1.8, 1.0)

        assertEquals(
            listOf(
                RelativeLocation(1.0, 12.0, 0.0),
                RelativeLocation(1.0, 8.8, 0.0),
            ),
            points.create()
        )
    }
}
