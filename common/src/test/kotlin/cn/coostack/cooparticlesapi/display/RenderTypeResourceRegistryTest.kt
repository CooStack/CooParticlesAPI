package cn.coostack.cooparticlesapi.display

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderTypeResourceRegistryTest {
    @Test
    fun `render type resource registry and bundled json descriptors exist`() {
        val registrySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypeResourceRegistry.kt"
        )
        val providerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/CooRenderTypesProvider.kt"
        )
        val reloadSupportSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooShaderReloadSupport.kt"
        )
        val reloadBusSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderReloadBus.kt"
        )
        val fabricClientSource = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt"
        )
        val fabricMainSource = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabric.kt"
        )
        val fabricListenerSource = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooShaderReloadListenerFabric.kt"
        )
        val fabricRenderTypesProvider = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/platform/FabricRenderTypesProvider.kt"
        )
        val neoRenderTypesProvider = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/platform/NeoRenderTypesProvider.kt"
        )

        assertTrue("object CooRenderTypeResourceRegistry" in registrySource)
        assertTrue("fun reloadFromClasspath()" in registrySource)
        assertTrue("fun reload(resourceManager: ResourceManager)" in registrySource)
        assertTrue("resourceManager.listResources(\"rendertypes\")" in registrySource)
        assertTrue("fun get(id: ResourceLocation): CooRenderTypeDescriptor?" in registrySource)
        assertTrue("fun getLayered(id: ResourceLocation): CooLayeredRenderTypeDescriptor?" in registrySource)
        assertTrue("fun named(id: ResourceLocation): RenderType?" in providerSource)
        assertTrue("fun layered(id: ResourceLocation): CooLayeredRenderType?" in providerSource)
        assertTrue("ShaderReloadSignal.FullReload(resourceManager)" in reloadSupportSource)
        assertTrue("ShaderBufferCache.releaseAll()" in reloadBusSource)
        assertTrue("CooRenderTypeResourceRegistry.reload(resourceManager)" in reloadBusSource)
        assertTrue("CooParticlesAPIClient.reloadShaderPrograms()" in reloadBusSource)
        assertTrue("object CooShaderReloadListenerFabric" in fabricListenerSource)
        assertTrue("ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)" in fabricListenerSource)
        assertTrue("CooShaderReloadListenerFabric.register()" in fabricClientSource)
        assertFalse("registerReloadListener(CooShaderReloadListener)" in fabricMainSource)
        assertTrue("return IrisCompat.wrapEntityRenderType(renderType)" in fabricRenderTypesProvider)
        assertTrue("return IrisCompat.wrapEntityRenderType(renderType)" in neoRenderTypesProvider)

        assertTrue(projectFile("common/src/main/resources/assets/cooparticlesapi/rendertypes/index.json").toFile().exists())
        assertTrue(projectFile("common/src/main/resources/assets/cooparticlesapi/rendertypes/glow.json").toFile().exists())
        assertTrue(projectFile("common/src/main/resources/assets/cooparticlesapi/rendertypes/glow_overlay.json").toFile().exists())
        assertTrue(projectFile("common/src/main/resources/assets/cooparticlesapi/rendertypes/glow_layered.json").toFile().exists())
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
