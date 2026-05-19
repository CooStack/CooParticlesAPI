package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderEntityAutoRegisterTest {
    @Test
    fun `auto render entity delegates codec generation and profile loading to shared helpers`() {
        val autoRenderEntitySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/AutoRenderEntity.kt"
        )

        assertTrue("RenderEntityHelper.generateCodec(this)" in autoRenderEntitySource)
        assertTrue("CodecHelper.updateFields(this, another)" in autoRenderEntitySource)
    }

    @Test
    fun `auto registry supports no arg and level vec3 constructors`() {
        val registrySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityAutoRegistry.kt"
        )

        assertTrue("type.getConstructor()" in registrySource)
        assertTrue("type.getConstructor(Level::class.java, Vec3::class.java)" in registrySource)
        assertTrue(
            "RenderEntity requires public no-arg or (Level, Vec3) constructor" in registrySource
        )
    }

    @Test
    fun `auto register stores generated codecs without requiring a renderer factory`() {
        val registrySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityAutoRegistry.kt"
        )
        val clientRegistrySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/ClientRenderEntityRegistry.kt"
        )

        assertTrue("ClientRenderEntityRegistry.get(id) != null" in registrySource)
        assertTrue("ClientRenderEntityRegistry.register(id, instance.getCodec())" in registrySource)
        assertTrue("rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null" in clientRegistrySource)
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
