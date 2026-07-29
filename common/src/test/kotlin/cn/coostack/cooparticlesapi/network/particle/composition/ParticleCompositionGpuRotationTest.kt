package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.test.options.particle.composition.TestCParticleComposition
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticleCompositionGpuRotationTest {
    @Test
    fun `gpu rotation preserves cpu orientation across yaw changes`() {
        val from = RelativeLocation.yAxis()
        val pitch = PI / 9.0

        listOf(0.0, PI / 7.0).forEach { roll ->
            listOf(-PI / 2.0, -PI / 4.0, 0.0, PI / 4.0, PI / 2.0).forEach { yaw ->
                val to = RelativeLocation(
                    -sin(yaw) * cos(pitch),
                    sin(pitch),
                    cos(yaw) * cos(pitch),
                )
                val expected = cpuRotatedRight(from, to, roll)

                val actual = gpuRotationMatrix(from, to, roll).transformDirection(Vector3f(1f, 0f, 0f))

                assertVectorEquals(expected, actual, "yaw=$yaw roll=$roll")
            }
        }
    }

    @Test
    fun `gpu rotation composes consecutive direction changes`() {
        val first = direction(142.5, 27.2)
        val second = RelativeLocation(0.2, -0.7, 0.5).normalize()
        val targets = listOf(first, second, second.clone(), -second)
        val rolls = listOf(0.0, PI / 8.0, -PI / 11.0, 0.0)
        val sourceBasis = listOf(
            Vector3f(1f, 0f, 0f),
            Vector3f(0f, 1f, 0f),
            Vector3f(0f, 0f, 1f),
        )
        val expectedBasis = sourceBasis.map(::Vector3f)
        val composition = TestCParticleComposition(Vec3.ZERO, null)
        var from = RelativeLocation.yAxis()

        targets.zip(rolls).forEachIndexed { step, (to, roll) ->
            expectedBasis.forEach { rotateCpu(it, from, to, roll) }
            val matrix = gpuRotationMatrix(composition, from, to, roll)

            sourceBasis.forEachIndexed { axis, source ->
                val actual = matrix.transformDirection(Vector3f(source))
                assertVectorEquals(expectedBasis[axis], actual, "step=$step axis=$axis")
            }
            from = to
        }
    }

    @Test
    fun `gpu rotation uses one y axis fallback for zero directions`() {
        val target = direction(-60.0, 15.0)
        val roll = PI / 7.0

        val expectedFromZero = cpuRotatedRight(RelativeLocation.yAxis(), target, roll)
        val actualFromZero = gpuRotationMatrix(RelativeLocation.zero(), target, roll)
            .transformDirection(Vector3f(1f, 0f, 0f))
        assertVectorEquals(expectedFromZero, actualFromZero, "zero from")

        val expectedToZero = cpuRotatedRight(target, RelativeLocation.yAxis(), roll)
        val actualToZero = gpuRotationMatrix(target, RelativeLocation.zero(), roll)
            .transformDirection(Vector3f(1f, 0f, 0f))
        assertVectorEquals(expectedToZero, actualToZero, "zero to")
    }

    private fun cpuRotatedRight(from: RelativeLocation, to: RelativeLocation, roll: Double): Vector3f {
        return rotateCpu(Vector3f(1f, 0f, 0f), from, to, roll)
    }

    private fun rotateCpu(vector: Vector3f, from: RelativeLocation, to: RelativeLocation, roll: Double): Vector3f {
        val normalizedFrom = from.normalize()
        val rollRotation = Quaternionf().rotateAxis(
            roll.toFloat(),
            normalizedFrom.x.toFloat(),
            normalizedFrom.y.toFloat(),
            normalizedFrom.z.toFloat(),
        )
        val undoFrom = Quaternionf()
            .rotateY(Math3DUtil.getYawFromLocation(normalizedFrom).toFloat())
            .rotateLocalX(Math3DUtil.getPitchFromLocation(normalizedFrom).toFloat())
        val normalizedTo = to.normalize()
        val applyTo = Quaternionf()
            .rotateY(-Math3DUtil.getYawFromLocation(normalizedTo).toFloat())
            .rotateX(-Math3DUtil.getPitchFromLocation(normalizedTo).toFloat())
        return vector.rotate(rollRotation).rotate(undoFrom).rotate(applyTo)
    }

    private fun gpuRotationMatrix(from: RelativeLocation, to: RelativeLocation, roll: Double): Matrix4f {
        return gpuRotationMatrix(TestCParticleComposition(Vec3.ZERO, null), from, to, roll)
    }

    private fun gpuRotationMatrix(
        composition: ParticleComposition,
        from: RelativeLocation,
        to: RelativeLocation,
        roll: Double,
    ): Matrix4f {
        val method = ParticleComposition::class.java.getDeclaredMethod(
            "applyGpuRotationTo",
            RelativeLocation::class.java,
            RelativeLocation::class.java,
            Double::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        method.invoke(composition, from, to, roll)

        return Matrix4f(
            ParticleComposition::class.java.getDeclaredField("cParticleLinearTransform")
                .apply { isAccessible = true }
                .get(composition) as Matrix4f,
        )
    }

    private fun direction(yawDegrees: Double, pitchDegrees: Double): RelativeLocation {
        val yaw = Math.toRadians(yawDegrees)
        val pitch = Math.toRadians(pitchDegrees)
        return RelativeLocation(
            -sin(yaw) * cos(pitch),
            sin(pitch),
            cos(yaw) * cos(pitch),
        )
    }

    private fun assertVectorEquals(expected: Vector3f, actual: Vector3f, context: String) {
        assertEquals(expected.x.toDouble(), actual.x.toDouble(), 1.0E-5, "x at $context")
        assertEquals(expected.y.toDouble(), actual.y.toDouble(), 1.0E-5, "y at $context")
        assertEquals(expected.z.toDouble(), actual.z.toDouble(), 1.0E-5, "z at $context")
    }
}
