package cn.coostack.cooparticlesapi.renderer.effects

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltInFrameEffectMigrationTest {
    @Test
    fun `built in fullscreen effects are contributed from instances to descriptor graph`() {
        val instanceSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )
        val managerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )

        assertTrue("BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)" in instanceSource)

        assertFalse("ClientWorldLightManager.render(entities.values.map { it.entity }" in managerSource)
        assertFalse("ClientPersistentBloomManager" in managerSource)
        assertFalse("ClientScreenGlowManager" in managerSource)
    }

    @Test
    fun `glow is exposed through post effect chains instead of legacy providers`() {
        val postTypesSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/BuiltinPostEffectTypes.kt"
        )
        val descriptorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/effects/builtin/BuiltinRenderEffectDescriptors.kt"
        )

        assertTrue("val BLOOM: PostEffectType" in postTypesSource)
        assertTrue("val HALO: PostEffectType" in postTypesSource)
        assertTrue("bright_extract" in postTypesSource)
        assertTrue("halo_composite" in postTypesSource)
        assertFalse("ScreenGlowProvider" in descriptorSource)
        assertFalse("PersistentBloomContextProvider" in descriptorSource)
        assertFalse("postGlowSphere(" in descriptorSource)
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
