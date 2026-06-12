package cn.coostack.cooparticlesapi.display

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class LayeredRenderTypeTest {
    @Test
    fun `layered render type api exposes descriptor layered handle and fan-out consumer`() {
        val descriptorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypeDescriptor.kt"
        )
        val layeredSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooLayeredRenderType.kt"
        )
        val providerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypesProvider.kt"
        )

        assertTrue("class CooRenderTypeDescriptor" in descriptorSource)
        assertTrue("class CooRenderTypeDescriptorBuilder" in descriptorSource)
        assertTrue("class CooLayeredRenderType" in layeredSource)
        assertTrue("class LayeredVertexConsumer" in layeredSource)
        assertTrue("fun create(descriptor: CooRenderTypeDescriptor): RenderType" in providerSource)
        assertTrue("fun layered(name: String, vararg layers: RenderType): CooLayeredRenderType" in providerSource)
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
