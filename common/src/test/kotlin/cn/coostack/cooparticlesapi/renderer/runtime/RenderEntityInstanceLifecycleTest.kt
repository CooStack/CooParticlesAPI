package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderEntityInstanceLifecycleTest {
    @Test
    fun `render entity instance type owns mirrored entity renderer and lifecycle hooks`() {
        val instancePath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt")

        assertTrue(instancePath.exists(), "Expected RenderEntityInstance.kt to exist")

        val instanceSource = Files.readString(instancePath)
        assertTrue("class RenderEntityInstance" in instanceSource)
        assertTrue("val entity" in instanceSource)
        assertTrue("val renderer" in instanceSource)
        assertTrue("var visualProfile" in instanceSource)
        assertTrue("fun initialize()" in instanceSource)
        assertTrue("fun updateFrom(" in instanceSource)
        assertTrue("fun markRemoved()" in instanceSource)
        assertTrue("fun release()" in instanceSource)
    }

    @Test
    fun `client render entity manager stores instances instead of raw entities`() {
        val managerSource = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt")

        assertTrue("HashMap<UUID, RenderEntityInstance<RenderEntity>>" in managerSource)
        assertTrue("fun getFrom(uuid: UUID): RenderEntityInstance<RenderEntity>?" in managerSource)
        assertTrue("fun add(instance: RenderEntityInstance<RenderEntity>)" in managerSource)
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
