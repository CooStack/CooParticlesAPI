package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderProgramRegistryLifecycleTest {
    @AfterTest
    fun tearDown() {
        ShaderProgramRegistry.releaseAll()
    }

    @Test
    fun `invalidate keeps registrations and reinitialize restores managed programs`() {
        val graphics = FakeGraphicsProgram()
        val compute = FakeComputeProgram()

        ShaderProgramRegistry.register(graphics)
        ShaderProgramRegistry.register(compute)

        graphics.init()
        compute.init()

        ShaderProgramRegistry.invalidateAll()

        assertEquals(0, graphics.program)
        assertEquals(0, compute.program)
        assertEquals(1, ShaderProgramRegistry.graphicsCount())
        assertEquals(1, ShaderProgramRegistry.computeCount())

        ShaderProgramRegistry.reinitializeAll()

        assertEquals(1, graphics.program)
        assertEquals(1, compute.program)
        assertEquals(2, graphics.initCalls)
        assertEquals(2, compute.initCalls)
    }

    @Test
    fun `registry source exposes incremental invalidation apis and program metadata hooks`() {
        val registrySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderProgramRegistry.kt"
        )
        val graphicsSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/CooShaderProgram.kt"
        )
        val computeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/api/CooComputeShaderProgram.kt"
        )
        val builderSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/AdvancedShaderProgramBuilder.kt"
        )
        val bufferCacheSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/buffer/ShaderBufferCache.kt"
        )
        val coordinatorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderCompileUpdateCoordinator.kt"
        )
        val reloadBusSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/shader/ShaderReloadBus.kt"
        )
        val reloadSupportSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooShaderReloadSupport.kt"
        )
        val clientSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )

        assertTrue("fun invalidateProgramsById(ids: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun invalidateProgramsBySource(sources: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun reinitializeProgramsById(ids: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun reinitializeProgramsBySource(sources: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun refreshProgramsById(ids: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun refreshProgramsBySource(sources: Set<ResourceLocation>): Int" in registrySource)
        assertTrue("fun refreshProgramsAndBuffersById(ids: Set<ResourceLocation>): ShaderRefreshResult" in registrySource)
        assertTrue("fun refreshProgramsAndBuffersBySource(sources: Set<ResourceLocation>): ShaderRefreshResult" in registrySource)
        assertTrue("fun managedProgramId(): ResourceLocation? = null" in graphicsSource)
        assertTrue("fun shaderSources(): Set<ResourceLocation>" in graphicsSource)
        assertTrue("fun managedProgramId(): ResourceLocation? = null" in computeSource)
        assertTrue("fun shaderSources(): Set<ResourceLocation>" in computeSource)
        assertTrue("fun managedId(id: ResourceLocation): AdvancedShaderProgramBuilder" in builderSource)
        assertTrue("fun managedId(path: String): AdvancedShaderProgramBuilder" in builderSource)
        assertTrue("fun releaseLayouts(layouts: Collection<ShaderBufferLayout<*>>): Int" in bufferCacheSource)
        assertTrue("data class ShaderCompileUpdate(" in coordinatorSource)
        assertTrue("fun handleUpdate(update: ShaderCompileUpdate): ShaderRefreshResult" in coordinatorSource)
        assertTrue("fun handleUpdatedProgramIds(ids: Set<ResourceLocation>): ShaderRefreshResult" in coordinatorSource)
        assertTrue("fun handleUpdatedShaderSources(sources: Set<ResourceLocation>): ShaderRefreshResult" in coordinatorSource)
        assertTrue("sealed interface ShaderReloadSignal" in reloadBusSource)
        assertTrue("object ShaderReloadBus" in reloadBusSource)
        assertTrue("fun register(listener: ShaderReloadListener): ShaderReloadListener" in reloadBusSource)
        assertTrue("ShaderCompileUpdateCoordinator.handleUpdate(signal.update)" in reloadBusSource)
        assertTrue("fun refreshProgramsAndBuffersById(ids: Set<ResourceLocation>): ShaderRefreshResult" in reloadSupportSource)
        assertTrue("fun handleCompileUpdate(update: ShaderCompileUpdate): ShaderRefreshResult" in reloadSupportSource)
        assertTrue("fun registerReloadListener(listener: ShaderReloadListener): ShaderReloadListener" in reloadSupportSource)
        assertTrue("fun unregisterReloadListener(listener: ShaderReloadListener)" in reloadSupportSource)
        assertTrue("VeilShaderCompileBridge" !in clientSource)
    }

    private class FakeGraphicsProgram : CooShaderProgram {
        override var program: Int = 0
        override var vertexShader: GlShader = FakeShader(GlShaderType.VERTEX)
        override var fragmentShader: GlShader = FakeShader(GlShaderType.FRAGMENT)
        var initCalls: Int = 0

        override fun init() {
            initCalls++
            program = 1
        }

        override fun use() {}
        override fun reset() {}
        override fun release() {
            program = 0
        }

        override fun useOnContext(drawMethod: CooShaderProgram.() -> Unit) {}
    }

    private class FakeComputeProgram : CooComputeShaderProgram {
        override var program: Int = 0
        override var computeShader: GlShader = FakeShader(GlShaderType.COMPUTE)
        var initCalls: Int = 0

        override fun init() {
            initCalls++
            program = 1
        }

        override fun use() {}
        override fun reset() {}
        override fun release() {
            program = 0
        }

        override fun dispatch(x: Int, y: Int, z: Int) {}
        override fun useOnContext(dispatchMethod: CooComputeShaderProgram.() -> Unit) {}
    }

    private class FakeShader(
        override val type: GlShaderType
    ) : GlShader {
        override fun shaderID(): Int = 0
        override fun compile() {}
        override fun assertCompiled() {}
        override fun deleteShader() {}
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
