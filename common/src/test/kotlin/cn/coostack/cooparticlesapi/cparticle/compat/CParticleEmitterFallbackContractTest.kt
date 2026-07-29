package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.config.APIConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleEmitterFallbackContractTest {
    /**
     * Verifies that the data type selects GPU or full CPU particle semantics.
     *
     * Example: plain ControlableParticleData keeps collision and sign callbacks.
     * Forbidden: emitters must not restore a shared use/configure hook.
     */
    @Test
    fun `emitters can selectively keep cpu particle semantics`() {
        val emitterSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ClassParticleEmitters.kt"
        )
        val bridgeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleEmitterBridge.kt"
        )
        val displayerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleDisplayer.kt"
        )

        assertTrue("if (data is ControlableCParticleData &&" in emitterSource)
        assertFalse("useCParticleSystem" in emitterSource)
        assertFalse("shouldUseCParticleSystem" in emitterSource)
        assertFalse("configureCParticleSystem" in emitterSource)
        assertFalse("cparticleUpdateMode" in emitterSource)
        assertFalse("cparticleCapacity" in emitterSource)
        assertTrue("open fun cparticleForces(): List<CParticleForce>" in emitterSource)
        assertTrue("for (force in emitter.cparticleForces())" in bridgeSource)
        assertTrue("if (!CParticleSystemManager.enabled) return false" in bridgeSource)
        assertTrue("val resolved = p.resolveTextures(pos)" in bridgeSource)
        assertTrue("if (!resolved.isValid) return true" in bridgeSource)
        assertTrue("resolved.base.bindingKey" in bridgeSource)
        assertTrue("resolved.mask?.bindingKey" in bridgeSource)
        assertTrue("system.spawnResolved(" in bridgeSource)
        assertTrue("resolveEffect" !in bridgeSource)
        assertTrue("if (!CParticleSystemManager.enabled) return null" in displayerSource)
        assertTrue("autoReleaseWhenEmpty = true" in displayerSource)
    }

    /**
     * Verifies that emitter pools expand by segment until the configurable global limit is reached.
     *
     * Example: a full local segment selects another GPU system instead of using a CPU particle.
     * Forbidden: reaching either a segment boundary or the global limit must not trigger CPU fallback.
     */
    @Test
    fun `emitter gpu pools use the global limit without cpu fallback`() {
        val bridgeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleEmitterBridge.kt"
        )
        val configSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/config/APIConfig.kt"
        )
        val systemSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val displayerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleDisplayer.kt"
        )
        val config = APIConfig()

        assertTrue("var cparticleCountLimit = 3_000_000" in configSource)
        assertEquals(3_000_000, config.cparticleCountLimit)
        config.cparticleCountLimit = 0
        assertEquals(1, config.cparticleCountLimit)
        assertTrue("if (!CParticleSystemManager.hasAvailableParticleCapacity()) return true" in bridgeSource)
        assertTrue(
            bridgeSource.indexOf("if (!resolved.isValid) return true") <
                    bridgeSource.indexOf("if (!CParticleSystemManager.hasAvailableParticleCapacity()) return true")
        )
        assertTrue("findAvailableSystem(" in bridgeSource)
        assertTrue("CParticleEmitterSegmentCursor(" in bridgeSource)
        assertTrue("return cursor.findAvailable()" in bridgeSource)
        assertTrue("systemCursors.remove(emitterId)" in bridgeSource)
        assertTrue("if (system.store.isFull()) return false" !in bridgeSource)
        assertTrue("CParticleSystemManager.hasAvailableParticleCapacity()" in systemSource)
        assertTrue("while (slot < 0 && CParticleSystemManager.hasAvailableParticleCapacity())" in displayerSource)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(findRepoRoot().resolve(relativePath))
    }

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
