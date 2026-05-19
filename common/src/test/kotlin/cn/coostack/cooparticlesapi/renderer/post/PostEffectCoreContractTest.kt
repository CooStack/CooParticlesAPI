package cn.coostack.cooparticlesapi.renderer.post

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PostEffectCoreContractTest {
    @Test
    fun `post effect type builder exposes render type style declaration`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectType.kt"
        )

        assertTrue("object CooPostEffectTypes" in source)
        assertTrue("fun register(id: ResourceLocation" in source)
        assertTrue("class PostEffectTypeBuilder" in source)
        assertTrue("fun screenQuad()" in source)
        assertTrue("fun pass(name: String" in source)
        assertTrue("): PostEffectPassRef" in source)
        assertTrue("PostEffectPassRef" in source)
        assertTrue("Declare passes directly with pass(...)" in source)
        assertTrue("Use pass(...) as the single post pass declaration API." in source)
    }

    @Test
    fun `post effect chain models passes inputs outputs and uniforms`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectChain.kt"
        )

        assertTrue("data class PostEffectChain" in source)
        assertTrue("data class PostEffectPass" in source)
        assertTrue("enum class PostEffectInputSource" in source)
        assertTrue("SCENE_COLOR" in source)
        assertTrue("SCENE_DEPTH" in source)
        assertTrue("MASK" in source)
        assertTrue("BRIGHT_COLOR" in source)
        assertTrue("CUSTOM_TEXTURE" in source)
        assertTrue("PASS_OUTPUT" in source)
        assertTrue("SCENE_RESOURCE" in source)
        assertTrue("enum class PostEffectResourceChannel" in source)
        assertTrue("fun inputCustomTexture" in source)
        assertTrue("class PostEffectPassRef" in source)
        assertTrue("fun asInputTo(" in source)
        assertTrue("fun asInputFrom(" in source)
        assertTrue("fun inputFromPass" in source)
        assertTrue("fun inputSceneResource" in source)
        assertTrue("fun inputSceneResourceDepth" in source)
        assertTrue("val textureSlot: Int? = null" in source)
        assertTrue("textureSlot: Int? = null" in source)
        assertTrue("duplicate texture input slot" in source)
    }

    @Test
    fun `builtin bloom declares bright extract blur and composite passes`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/BuiltinPostEffectTypes.kt"
        )
        val compositeShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/bloom_composite.fsh"
        )
        val downsampleShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/bloom_downsample.fsh"
        )
        val upsampleShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/bloom_upsample.fsh"
        )

        assertTrue("val BLOOM" in source)
        assertTrue("pass(\"bright_extract\"" in source)
        assertTrue("pass(\"blur_horizontal\"" in source)
        assertTrue("pass(\"blur_vertical\"" in source)
        assertTrue("pass(\"composite\"" in source)
        assertTrue("RenderBackendCapability.SCENE_COLOR_COPY" in source)
        assertTrue("uniform(\"iterations\")" in source)
        assertTrue("uniform(\"mipLevels\")" in source)
        assertTrue("it.params[\"mipLevel\"]" in source)
        assertTrue("sampleMipTent" in compositeShader)
        assertTrue("textureLod(bright" in compositeShader)
        assertTrue("MAX_BLOOM_MIPS" in compositeShader)
        assertTrue("sampleTent" in downsampleShader)
        assertTrue("sampleTent" in upsampleShader)
        assertTrue("uniform float upsampleRadius" in upsampleShader)
    }

    @Test
    fun `builtin shockwave halo and binding presets expose required params`() {
        val builtinSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/BuiltinPostEffectTypes.kt"
        )
        val instanceSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectInstance.kt"
        )
        val backendSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val haloMaskShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/halo_mask.fsh"
        )
        val bindingMaskShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/binding_mask.fsh"
        )

        assertTrue("uniform(\"strength\")" in builtinSource)
        assertTrue("uniform(\"throughWalls\")" in builtinSource)
        assertTrue("fun bindBlock(" in instanceSource)
        assertTrue("offset: PostEffectParamValue.Vec3Value" in instanceSource)
        assertTrue("resolveBindingCenter" in backendSource)
        assertTrue("uniform vec2 center" in readProjectFile("common/src/main/resources/assets/cooparticlesapi/shaders/post/shockwave.fsh"))
        assertTrue("post/binding_mask.fsh" in backendSource)
        assertTrue("resolveBindingDepth" in backendSource)
        assertTrue("uniform sampler2D depth" in haloMaskShader)
        assertTrue("uniform bool hasDepth" in haloMaskShader)
        assertTrue("sourceDepth - sceneDepth" in haloMaskShader)
        assertTrue("uniform vec2 screenSize" in bindingMaskShader)
        assertTrue("screenSize.x / max(screenSize.y, 1.0)" in bindingMaskShader)
    }

    @Test
    fun `post effect lifecycle has explicit phase progress and expiry`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectLifecycle.kt"
        )

        assertTrue("val progress: Float" in source)
        assertTrue("val phase: PostEffectLifecyclePhase" in source)
        assertTrue("val expired: Boolean" in source)
        assertTrue("WARMUP" in source)
        assertTrue("EXPAND" in source)
        assertTrue("HOLD" in source)
        assertTrue("FADE" in source)
    }

    @Test
    fun `post effect runtime has chain executor bridge`() {
        val runtimeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectRuntimeRegistry.kt"
        )
        val executorSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/PostEffectFrameExecutor.kt"
        )

        assertTrue("PostEffectFrameExecutor.execute(context, instances)" in runtimeSource)
        assertTrue("object PostEffectFrameExecutor" in executorSource)
        assertTrue("data class PostEffectExecutionStep" in executorSource)
        assertTrue("fun interface PostEffectExecutionBackend" in executorSource)
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
