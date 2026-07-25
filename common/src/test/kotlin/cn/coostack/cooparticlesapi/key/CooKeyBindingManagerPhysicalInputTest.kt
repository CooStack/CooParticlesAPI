package cn.coostack.cooparticlesapi.key

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooKeyBindingManagerPhysicalInputTest {
    @Test
    fun `listening mappings use vanilla conflict detection again`() {
        val mixinSource = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/KeyMappingMixin.java"
        )

        assertFalse("method = \"same\"" in mixinSource)
        assertFalse("cir.setReturnValue(false)" in mixinSource)
        assertFalse("CooListeningKeyMapping" in mixinSource)
    }

    @Test
    fun `same-key click and down events broadcast from key mapping events`() {
        val mixinSource = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/KeyMappingMixin.java"
        )

        assertTrue("method = \"click\"" in mixinSource)
        assertTrue("method = \"set\"" in mixinSource)
        assertTrue("cooparticlesapi\$incrementClickCount" in mixinSource)
        assertTrue("mapping.setDown(isDown)" in mixinSource)
        assertTrue("MAP.get(key)" in mixinSource)
        assertTrue("ALL.values()" in mixinSource)
    }

    @Test
    fun `key mapping mixins are fabric-only`() {
        val commonConfig = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")
        val fabricConfig = readProjectFile("fabric/src/main/resources/cooparticlesapi.fabric.mixins.json")

        assertFalse("\"KeyMappingAccessor\"" in commonConfig)
        assertFalse("\"KeyMappingMixin\"" in commonConfig)
        assertTrue("\"KeyMappingAccessor\"" in fabricConfig)
        assertTrue("\"KeyMappingMixin\"" in fabricConfig)
    }

    @Test
    fun `manager does not synthesize vanilla click counts from tick polling`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/key/CooKeyBindingManager.kt"
        )

        assertTrue("state.mapping.setDown(down)" in source)
        assertFalse("syncClickMappingsWithSameKey" in source)
        assertFalse("cooparticlesapi\$setClickCount" in source)
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
