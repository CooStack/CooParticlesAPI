package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CParticleNewbornLifecycleTest {
    @Test
    fun `new particle keeps its initial state for the spawn tick`() {
        val store = CParticleStore(1)
        val slot = store.spawn(
            CParticle().apply {
                velocity = Vec3(1.0, 0.0, 0.0)
                maxAge = 20
            },
            Vec3.ZERO,
            animationId = 0,
            blockLight = 15,
            skyLight = 15,
        )

        simulate(store)
        store.tickAges(writeBufferAge = true)

        assertEquals(0f, store.data[slot * CParticleStore.STRIDE])
        assertEquals(0, store.getAge(slot))
        assertEquals(
            0,
            store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and
                CParticleStore.FLAG_NEWBORN,
        )

        simulate(store)
        store.tickAges(writeBufferAge = true)

        assertEquals(1f, store.data[slot * CParticleStore.STRIDE])
        assertEquals(1, store.getAge(slot))
        store.clearSpawned()
    }

    @Test
    fun `system spawn uses the next tick as its visual epoch`() {
        val system = CParticleSystem(
            name = "newborn-lifecycle-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val slot = system.spawn(
            CParticle().apply { maxAge = 20 },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )

        assertEquals(
            1f,
            system.store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_EPOCH_TICK],
        )
    }

    @Test
    fun `spawn tick setters preserve the next visual epoch`() {
        val system = CParticleSystem(
            name = "newborn-setter-epoch-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val slot = system.spawn(
            CParticle().apply { maxAge = 20 },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        val generation = system.store.generations[slot]
        val epochOffset = slot * CParticleStore.STRIDE + CParticleStore.OFF_EPOCH_TICK

        system.scriptedSetAge(slot, generation, 7)
        assertEquals(1f, system.store.data[epochOffset])
        system.scriptedSetRotation(slot, generation, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, system.store.data[epochOffset])
        system.scriptedSetRotationDirection(slot, generation, Vector3f(1f, 0f, 0f))
        assertEquals(1f, system.store.data[epochOffset])
        system.scriptedSetAngularVelocity(slot, generation, Vector3f(0.1f, 0.2f, 0.3f))
        assertEquals(1f, system.store.data[epochOffset])
        system.scriptedAddRoll(slot, generation, 0.5f)
        assertEquals(1f, system.store.data[epochOffset])
    }

    @Test
    fun `reused slot keeps its replacement in the spawn phase`() {
        val store = CParticleStore(1)
        val first = store.spawn(CParticle(), Vec3.ZERO, 0, 15, 15)
        store.kill(first)
        val replacement = store.spawn(
            CParticle().apply {
                velocity = Vec3(1.0, 0.0, 0.0)
                maxAge = 20
            },
            Vec3.ZERO,
            0,
            15,
            15,
        )

        assertEquals(first, replacement)
        simulate(store)
        store.tickAges(writeBufferAge = true)
        assertEquals(0f, store.data[replacement * CParticleStore.STRIDE])
        assertEquals(0, store.getAge(replacement))

        simulate(store)
        store.tickAges(writeBufferAge = true)
        assertEquals(1f, store.data[replacement * CParticleStore.STRIDE])
        assertEquals(1, store.getAge(replacement))
        store.clearSpawned()
    }

    @Test
    fun `gpu simulation skips and clears newborn slots`() {
        val shader = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/resources/assets/cooparticlesapi/shaders/core/compute/cparticle_sim.comp"
            )
        )
        val newbornBranch = shader.substringAfter("if ((flags & FLAG_NEWBORN) != 0)")
            .substringBefore("vec3 pos =")

        assertTrue("const int FLAG_NEWBORN = 1 << 16;" in shader)
        assertTrue("flags &= ~FLAG_NEWBORN;" in newbornBranch)
        assertTrue("particles[i].velFlags.w = float(flags);" in newbornBranch)
        assertTrue("return;" in newbornBranch)
    }

    private fun simulate(store: CParticleStore) {
        CParticleCpuSimulator.simulate(
            store,
            FloatArray(0),
            forceCount = 0,
            originX = 0.0,
            originY = 0.0,
            originZ = 0.0,
            speedLimit = 32f,
        )
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
