package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CParticleSnapshotTest {
    @Test
    fun `snapshot reads current slot state without retaining a live view`() {
        val system = scriptedSystem()
        val particle = CParticle().apply {
            pos = Vec3(10.0, 20.0, 30.0)
            velocity = Vec3(0.1, 0.2, 0.3)
            uniformSize = false
            weightSize = 0.4f
            heightSize = 0.8f
            color = Vector3f(0.2f, 0.4f, 0.6f)
            alpha = 0.75f
            age = 3
            maxAge = 40
            light = 9
            cameraOption = ParticleCameraOption.ROTATION
            axis = Vec3(1.0, 2.0, 3.0)
            yaw = 0.5f
            pitch = 0.6f
            roll = 0.7f
        }
        val slot = system.spawn(particle, CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val generation = system.store.generations[slot]
        val controler = CParticleControlable(system, slot, generation, UUID.randomUUID(), null)

        system.scriptedSetPos(slot, generation, Vec3(11.0, 21.0, 31.0))
        system.scriptedSetVelocity(slot, generation, Vec3(0.4, 0.5, 0.6))
        system.scriptedSetAge(slot, generation, 7)
        system.scriptedSetSize(slot, generation, 1.2f, 1.4f)
        system.scriptedSetColor(slot, generation, 0.7f, 0.8f, 0.9f)
        system.scriptedSetAlpha(slot, generation, 0.5f)
        system.scriptedSetRotation(slot, generation, 0.8f, 0.9f, 1.0f)

        val snapshot = assertNotNull(controler.snapshot())
        assertEquals(CParticleUpdateMode.STATIC, snapshot.updateMode)
        assertEquals(Vec3(11.0, 21.0, 31.0), snapshot.pos)
        assertEquals(0.4, snapshot.velocity.x, 1e-6)
        assertEquals(0.5, snapshot.velocity.y, 1e-6)
        assertEquals(0.6, snapshot.velocity.z, 1e-6)
        assertEquals(1.2f, snapshot.weightSize)
        assertEquals(1.4f, snapshot.heightSize)
        assertEquals(Vector3f(0.7f, 0.8f, 0.9f), snapshot.color)
        assertEquals(0.5f, snapshot.alpha)
        assertEquals(7, snapshot.age)
        assertEquals(40, snapshot.maxAge)
        assertEquals(9, snapshot.light)
        assertEquals(ParticleCameraOption.ROTATION, snapshot.cameraOption)
        assertEquals(Vec3(1.0, 2.0, 3.0), snapshot.axis)
        assertEquals(0.8f, snapshot.yaw)
        assertEquals(0.9f, snapshot.pitch)
        assertEquals(1.0f, snapshot.roll)

        snapshot.pos = Vec3.ZERO
        snapshot.color.set(1f, 1f, 1f)
        val secondSnapshot = assertNotNull(controler.snapshot())
        assertEquals(Vec3(11.0, 21.0, 31.0), secondSnapshot.pos)
        assertEquals(Vector3f(0.7f, 0.8f, 0.9f), secondSnapshot.color)
    }

    @Test
    fun `snapshot returns null after the slot is removed or reused`() {
        val system = scriptedSystem()
        val slot = system.spawn(CParticle(), CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val generation = system.store.generations[slot]
        val controler = CParticleControlable(system, slot, generation, UUID.randomUUID(), null)

        system.kill(slot, generation)
        assertNull(controler.snapshot())

        val reusedSlot = system.spawn(CParticle(), CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        assertEquals(slot, reusedSlot)
        assertNull(controler.snapshot())
    }

    @Test
    fun `snapshot rejects simulated systems with gpu authoritative movement`() {
        val system = CParticleSystem(
            name = "simulated-snapshot-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SIMULATED,
        )
        val slot = system.spawn(CParticle(), CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val controler = CParticleControlable(
            system,
            slot,
            system.store.generations[slot],
            UUID.randomUUID(),
            null,
        )

        assertNull(controler.snapshot())
    }

    @Test
    fun `static handles write gpu rotation state and snapshot returns accumulated angles`() {
        val system = scriptedSystem()
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            cameraOption = ParticleCameraOption.ROTATION
            axis = Vec3(0.0, 1.0, 0.0)
        }
        val slot = system.spawn(particle, CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val generation = system.store.generations[slot]
        val controler = CParticleControlable(system, slot, generation, UUID.randomUUID(), null)

        controler.pitch = 0.1f
        controler.yaw = 0.2f
        controler.roll = 0.3f
        controler.setAngularVelocity(0.01f, 0.02f, 0.03f)
        val base = slot * CParticleStore.STRIDE
        system.store.data[base + CParticleStore.OFF_EPOCH_TICK] = -4f

        val animated = assertNotNull(controler.snapshot())
        assertEquals(0.14f, animated.pitch, 1e-6f)
        assertEquals(0.28f, animated.yaw, 1e-6f)
        assertEquals(0.42f, animated.roll, 1e-6f)
        assertEquals(Vec3(0.0, 1.0, 0.0), animated.axis)
        assertNull(animated.rotationDirection)
        assertNull(system.store.dynamicSource(slot))

        controler.setRotationDirection(Vector3f(0f, 1f, 0f))
        controler.roll = 0.5f
        assertNotNull(system.store.rotationDirection(slot))
        controler.setRotationDirection(null)
        controler.setRotationDirection(Vector3f(0f, 1f, 0f))
        val pointed = assertNotNull(controler.snapshot())
        assertTrue(pointed.pitch.isFinite())
        assertEquals((Math.PI / 2.0).toFloat(), pointed.pitch, 1e-6f)
    }

    private fun scriptedSystem() = CParticleSystem(
        name = "snapshot-test",
        capacity = 1,
        layer = CParticleRenderLayer.OPAQUE,
        mode = CParticleSystemMode.SCRIPTED,
    )
}
