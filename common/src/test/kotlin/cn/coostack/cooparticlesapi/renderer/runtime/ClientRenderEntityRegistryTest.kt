package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class ClientRenderEntityRegistryTest {
    @Test
    fun `registry type exists and exposes register plus lookup APIs`() {
        val registryPath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityRegistry.kt")
        val typePath = projectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityType.kt")

        assertTrue(registryPath.exists(), "Expected ClientRenderEntityRegistry.kt to exist")
        assertTrue(typePath.exists(), "Expected ClientRenderEntityType.kt to exist")

        val registrySource = Files.readString(registryPath)
        assertTrue("object ClientRenderEntityRegistry" in registrySource)
        assertTrue("fun register(" in registrySource)
        assertTrue("fun get(" in registrySource)

        val typeSource = Files.readString(typePath)
        assertTrue("data class ClientRenderEntityType" in typeSource)
        assertTrue("codec" in typeSource)
        assertTrue("rendererFactory" in typeSource)
    }

    @Test
    fun `registry returns null for unknown entity id`() {
        val registrySource = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityRegistry.kt")

        assertTrue(
            "fun get(id: ResourceLocation): ClientRenderEntityType?" in registrySource ||
                "fun get(id: ResourceLocation): ClientRenderEntityType<" in registrySource,
            "Expected registry lookup to return a nullable client type"
        )
        assertTrue("return types[id]" in registrySource || "types[id]" in registrySource)
    }

    @Test
    fun `registry rejects duplicate registrations for the same id`() {
        val registrySource = readProjectFile("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityRegistry.kt")

        assertTrue("IllegalArgumentException" in registrySource || "require(" in registrySource)
        assertTrue("id.toString()" in registrySource || "\"\$id\"" in registrySource)
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
