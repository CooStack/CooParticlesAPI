package cn.coostack.cooparticlesapi.network.particle.composition

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ParticleCompositionLifecycleContractTest {
    @Test
    fun `toggle relative cleans stale particle controllers before teleporting`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/ParticleComposition.kt"
        )

        assertTrue("val staleControls = ArrayList<Controlable<*>>()" in source)
        assertTrue("particle is ParticleControler && !particle.isBound" in source)
        assertTrue("staleControls.forEach { removeDisplayedControl(it) }" in source)
        assertTrue("private fun removeDisplayedControl(control: Controlable<*>)" in source)
        assertTrue("control.remove(RemoveReason.QUEUE)" in source)
        assertTrue("particleLocations.remove(control)" in source)
        assertTrue("particles.remove(control.controlUUID())" in source)
        assertTrue("particleDefaultLength.remove(control.controlUUID())" in source)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
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
