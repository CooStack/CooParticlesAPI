package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable
import org.joml.Vector3f
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CParticleStoreTest {
    @Test
    fun `killing trailing slots shrinks the draw high water mark`() {
        val source = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/storage/CParticleStore.kt"
            )
        )

        assertTrue("if (slot + 1 == highWater)" in source)
        assertTrue("while (highWater > 0 && !isAlive(highWater - 1))" in source)
        assertTrue("packFlags(" in source)
        assertTrue("val flags = data[base + OFF_FLAGS].toInt()" in source)
    }

    @Test
    fun `scripted age updates the age base without resolving a sprite frame`() {
        val initialUv = CParticleSprites.UvRect(0f, 0f, 0.25f, 0.25f)
        val system = scriptedSystem()
        val particle = CParticle().apply {
            maxAge = 20
            updateMode = CParticleUpdateMode.DYNAMIC
        }
        val slot = system.spawn(particle, initialUv)
        val generation = system.store.generations[slot]

        system.scriptedSetAge(slot, generation, 7)

        assertEquals(7, system.store.getAge(slot))
        assertEquals(7f, system.store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_AGE])
        assertEquals(7f, system.store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_ANIMATION + 1])
        assertEquals(7, particle.age)

        var descriptorResolved = false
        val dirtyCount = system.store.prepareDynamicVisuals(
            tick = 0,
            textureGeneration = CParticleTextureResolver.generation,
            expectedBindingKey = CParticleTextureBindingKey.PARTICLE_ATLAS,
            resolveTexture = {
                descriptorResolved = true
                error("age changes must not resolve the texture descriptor")
            },
            onBindingMismatch = { _, _ -> },
        )
        assertEquals(0, dirtyCount)
        assertEquals(false, descriptorResolved)

        system.scriptedSetAge(slot, generation, Int.MAX_VALUE)
        assertEquals(20, system.store.getAge(slot))
        system.scriptedSetAge(slot, generation, -1)
        assertEquals(0, system.store.getAge(slot))

        system.store.kill(slot)
        val reusedSlot = system.spawn(CParticle().apply { maxAge = 30 }, initialUv)
        assertEquals(slot, reusedSlot)
        system.scriptedSetAge(slot, generation, 12)
        assertEquals(0, system.store.getAge(reusedSlot))
    }

    @Test
    fun `scripted alpha keeps controllable particle clamp semantics`() {
        val system = scriptedSystem()
        val slot = system.spawn(CParticle(), CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val generation = system.store.generations[slot]
        val alphaOffset = slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR + 3

        system.scriptedSetAlpha(slot, generation, 2f)
        assertEquals(1f, system.store.data[alphaOffset])
        system.scriptedSetAlpha(slot, generation, -0.5f)
        assertEquals(0f, system.store.data[alphaOffset])
    }

    @Test
    fun `static particle handle writes color alpha and size without source retention`() {
        val system = scriptedSystem()
        val particle = CParticle().apply { updateMode = CParticleUpdateMode.STATIC }
        val slot = system.spawn(particle, CParticleSprites.UvRect(0f, 0f, 1f, 1f))
        val generation = system.store.generations[slot]
        val control = CParticleControlable(system, slot, generation, UUID.randomUUID(), null)

        control.setColor(0.25f, 0.5f, 0.75f)
        control.setAlpha(0.4f)
        control.setSize(2f, 3f)

        assertEquals(false, system.store.hasDynamicStorage)
        val snapshot = control.snapshot()!!
        assertEquals(Vector3f(0.25f, 0.5f, 0.75f), snapshot.color)
        assertEquals(0.4f, snapshot.alpha)
        assertEquals(2f, snapshot.weightSize)
        assertEquals(3f, snapshot.heightSize)
    }

    @Test
    fun `spawn queue keeps each reused slot once without dropping later slots`() {
        val system = scriptedSystem(capacity = 2)
        val uv = CParticleSprites.UvRect(0f, 0f, 1f, 1f)
        val first = system.spawn(CParticle(), uv)
        system.store.kill(first)

        val reused = system.spawn(CParticle(), uv)
        val second = system.spawn(CParticle(), uv)

        assertEquals(first, reused)
        assertEquals(2, system.store.spawnedCount)
        assertEquals(setOf(reused, second), system.store.spawnedSlots.take(system.store.spawnedCount).toSet())
    }

    private fun scriptedSystem(capacity: Int = 1) = CParticleSystem(
        name = "test",
        capacity = capacity,
        layer = CParticleRenderLayer.OPAQUE,
        mode = CParticleSystemMode.SCRIPTED,
    )

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
