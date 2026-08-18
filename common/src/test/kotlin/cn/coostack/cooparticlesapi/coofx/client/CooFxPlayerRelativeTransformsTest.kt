package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CooFxPlayerRelativeTransformsTest {
    @Test
    fun `player facing maps Blender positive Z to Minecraft forward`() {
        val transform = CooFxPlayerRelativeTransforms.fromView(
            playerPosition = Vec3(10.0, 64.0, -3.0),
            viewYawDegrees = 90F,
            viewPitchDegrees = 0F,
            blenderLocalTransform = CooFxWorldTransform(x = 0.0, y = 0.0, z = 2.0),
        )

        assertClose(8.0, transform.x)
        assertClose(64.0, transform.y)
        assertClose(-3.0, transform.z)
    }

    @Test
    fun `player pitch updates the same Blender local transform without input ownership`() {
        val transform = CooFxPlayerRelativeTransforms.fromView(
            playerPosition = Vec3.ZERO,
            viewYawDegrees = 0F,
            viewPitchDegrees = 90F,
            blenderLocalTransform = CooFxWorldTransform(x = 0.0, y = 0.0, z = 2.0),
        )

        assertClose(0.0, transform.x)
        assertClose(-2.0, transform.y)
        assertClose(0.0, transform.z)
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(
            actual = abs(expected - actual) < 0.0001,
            message = "期望 $expected，实际为 $actual",
        )
    }
}
