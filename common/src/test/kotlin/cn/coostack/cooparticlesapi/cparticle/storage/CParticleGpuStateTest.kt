package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleGpuMath
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CParticleGpuStateTest {
    @Test
    fun `layout flags animation and seed are packed exactly`() {
        val store = CParticleStore(1)
        val seed = 0x89ABCDEF.toInt()
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            cameraOption = ParticleCameraOption.ROTATION
            rotationDirection = Vector3f(2f, 3f, 4f)
            randomAgePreTick = true
        }
        val slot = store.spawn(particle, Vec3.ZERO, 37, 5, 12, epochTick = 9, randomSeed = seed)
        val base = slot * CParticleStore.STRIDE
        val flags = store.data[base + CParticleStore.OFF_FLAGS].toInt()

        assertEquals(36, CParticleStore.STRIDE)
        assertEquals(144, CParticleStore.BYTE_STRIDE)
        assertEquals(
            particle.appearanceDescriptorId().toFloat(),
            store.data[base + CParticleStore.OFF_APPEARANCE],
        )
        assertTrue(flags and CParticleStore.FLAG_ALIVE != 0)
        assertTrue(flags and CParticleStore.FLAG_RANDOM_AGE != 0)
        assertTrue(flags and CParticleStore.FLAG_ROTATION_DIRECTION != 0)
        assertEquals(37f, store.data[base + CParticleStore.OFF_ANIMATION])
        assertEquals(
            seed,
            CParticleGpuMath.joinSeed(
                store.data[base + CParticleStore.OFF_ANIMATION + 2].toInt(),
                store.data[base + CParticleStore.OFF_ANIMATION + 3].toInt(),
            ),
        )
        assertEquals(flags, flags.toFloat().toInt())
        assertNull(store.dynamicSource(slot))
    }

    @Test
    fun `age reset rebases rotation and only changes the texture age base`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            maxAge = 100
            pitch = 1f
            yaw = 2f
            roll = 3f
            angularVelocity = Vector3f(0.1f, 0.2f, 0.3f)
        }
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15, epochTick = 10, randomSeed = 1)
        val before = store.currentRotation(slot, 15)

        store.setAge(slot, 7, epochTick = 15)

        assertEquals(before, store.currentRotation(slot, 15))
        assertEquals(7, store.getAge(slot))
        val base = slot * CParticleStore.STRIDE
        assertEquals(7f, store.data[base + CParticleStore.OFF_ANIMATION + 1])
        assertEquals(15f, store.data[base + CParticleStore.OFF_EPOCH_TICK])
        assertEquals(7f, store.data[base + CParticleStore.OFF_AGE])
    }

    @Test
    fun `changing angular velocity preserves the displayed angle at its epoch`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            cameraOption = ParticleCameraOption.ROTATION
            pitch = 0.1f
            yaw = 0.2f
            roll = 0.3f
            angularVelocity = Vector3f(0.01f, 0.02f, 0.03f)
        }
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15, epochTick = 0, randomSeed = 1)
        val atTen = store.currentRotation(slot, 10)

        val nextVelocity = Vector3f(0.4f, 0.5f, 0.6f)
        store.setAngularVelocity(slot, nextVelocity, tick = 10)

        assertEquals(atTen, store.currentRotation(slot, 10))
        val atEleven = store.currentRotation(slot, 11)
        assertEquals(atTen.x + nextVelocity.x, atEleven.x, 1e-6f)
        assertEquals(atTen.y + nextVelocity.y, atEleven.y, 1e-6f)
        assertEquals(atTen.z + nextVelocity.z, atEleven.z, 1e-6f)
    }

    @Test
    fun `setting the same direction preserves accumulated angular phase`() {
        val store = CParticleStore(1)
        val direction = Vector3f(1f, 2f, 3f)
        val particle = CParticle().apply {
            cameraOption = ParticleCameraOption.ROTATION
            rotationDirection = Vector3f(direction)
            angularVelocity = Vector3f(0.1f, 0.2f, 0.3f)
        }
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15, epochTick = 2, randomSeed = 1)
        val before = store.currentRotation(slot, 9)

        store.setRotationDirection(slot, Vector3f(direction), tick = 9)

        assertEquals(before, store.currentRotation(slot, 9))
    }

    @Test
    fun `billboard modes ignore pitch and yaw velocity`() {
        for (mode in listOf(ParticleCameraOption.BILLBOARD, ParticleCameraOption.AXIS_BILLBOARD)) {
            val store = CParticleStore(1)
            val particle = CParticle().apply {
                cameraOption = mode
                pitch = 1f
                yaw = 2f
                roll = 3f
                angularVelocity = Vector3f(10f, 20f, 0.5f)
            }
            val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15, epochTick = 2, randomSeed = 1)
            val rotation = store.currentRotation(slot, 6)
            assertEquals(1f, rotation.x)
            assertEquals(2f, rotation.y)
            assertEquals(5f, rotation.z)
        }
    }

    @Test
    fun `cpu simulation leaves gpu visual metadata unchanged`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            velocity = Vec3(0.25, 0.5, 0.75)
            angularVelocity = Vector3f(0.1f, 0.2f, 0.3f)
        }
        val slot = store.spawn(particle, Vec3.ZERO, 91, 15, 15, epochTick = 4, randomSeed = -7)
        val base = slot * CParticleStore.STRIDE
        val visualBefore = store.data.copyOfRange(base + CParticleStore.OFF_FLAGS, base + CParticleStore.STRIDE)

        CParticleCpuSimulator.simulate(store, FloatArray(0), 0, 0.0, 0.0, 0.0, 32f)

        assertTrue(
            visualBefore.contentEquals(
                store.data.copyOfRange(base + CParticleStore.OFF_FLAGS, base + CParticleStore.STRIDE)
            )
        )
    }

    @Test
    fun `static lifecycle age advances without dirtying instance data`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            maxAge = 20
        }
        val slot = store.spawn(particle, Vec3.ZERO, 5, 15, 15, epochTick = 3, randomSeed = 1)
        val base = slot * CParticleStore.STRIDE
        store.clearDirty()
        store.clearSpawned()

        store.tickAges(writeBufferAge = false)

        assertEquals(1, store.getAge(slot))
        assertEquals(-1, store.dirtyMin)
        assertEquals(0f, store.data[base + CParticleStore.OFF_ANIMATION + 1])
        assertEquals(3f, store.data[base + CParticleStore.OFF_EPOCH_TICK])
    }
}
