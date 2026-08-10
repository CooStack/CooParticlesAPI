package cn.coostack.cooparticlesapi.renderer.post

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostEffectCoreContractTest {
    @Test
    fun `legacy post facade and builders are deleted`() {
        val typeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectType.kt"
        )
        val chainSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectChain.kt"
        )

        assertFalse("CooPostEffectTypes" in typeSource)
        assertFalse("PostEffectTypeBuilder" in typeSource)
        assertFalse("PostEffectChainBuilder" in chainSource)
        assertFalse("PostEffectPassBuilder" in chainSource)
        assertFalse("PostEffectPassRef" in chainSource)
        assertFalse(projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/BuiltinPostEffectTypes.kt"
        ).toFile().exists())
    }

    @Test
    fun `post execution model is internal compiler output`() {
        val typeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectType.kt"
        )
        val chainSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectChain.kt"
        )
        val executorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        assertTrue("internal class PostEffectType" in typeSource)
        assertTrue("internal data class PostEffectChain" in chainSource)
        assertTrue("internal data class PostEffectPass" in chainSource)
        assertTrue("internal data class PostEffectInput" in chainSource)
        assertTrue("internal enum class PostEffectOutput" in chainSource)
        assertTrue("internal data class PostEffectExecutionPlan" in executorSource)
        assertTrue("internal fun interface PostEffectExecutionBackend" in executorSource)
        assertTrue("internal interface PostEffectFramePreparationBackend" in executorSource)
        assertTrue("internal interface PostEffectResourceBackend" in executorSource)
    }

    @Test
    fun `shader effect demo declares bloom as line connected ping pong graph`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/PostEffectDemoOptions.kt"
        )
        val shader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/bloom_ping_pong_blur.fsh"
        )

        assertTrue("CooShaderEffects.register" in source)
        assertTrue("pingPong(\"blur\", iterations = 4" in source)
        assertTrue("line(extract.color(), blur.input(\"bright\"))" in source)
        assertTrue("line(blur.color(), composite.input(\"bright\"))" in source)
        assertTrue("uniform int Axis" in shader)
        assertTrue("uniform int Iteration" in shader)
    }

    @Test
    fun `post runtime remains an internal pipeline compiler bridge`() {
        val runtimeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectRuntimeRegistry.kt"
        )

        assertTrue("internal object PostEffectRuntimeRegistry" in runtimeSource)
        assertTrue("PostEffectFrameExecutor.execute(context, instances)" in runtimeSource)
        assertTrue("types[type.id] = type" in runtimeSource)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor.resolve(relativePath)
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
