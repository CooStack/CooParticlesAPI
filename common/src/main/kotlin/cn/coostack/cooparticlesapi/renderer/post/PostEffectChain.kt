package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation

data class PostEffectChain(
    val passes: List<PostEffectPass>,
    val output: PostEffectOutput = PostEffectOutput.FINAL_SCREEN
) {
    init {
        require(passes.isNotEmpty()) { "A post effect chain must contain at least one pass" }
    }
}

data class PostEffectPass(
    val name: String,
    val fragment: ResourceLocation,
    val inputs: List<PostEffectInput> = emptyList(),
    val output: PostEffectOutput = PostEffectOutput.TEMPORARY,
    val uniforms: List<PostEffectUniform> = emptyList(),
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    val optionalCapabilities: Set<RenderBackendCapability> = emptySet()
)

data class PostEffectInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean = false
)

data class PostEffectUniform(
    val name: String,
    val provider: (PostEffectInstance) -> PostEffectParamValue?
)

enum class PostEffectInputSource {
    SCENE_COLOR,
    SCENE_DEPTH,
    MASK,
    BRIGHT_COLOR,
    CUSTOM_TEXTURE
}

enum class PostEffectOutput {
    TEMPORARY,
    MASK,
    BLOOM,
    FINAL_SCREEN
}

enum class PostEffectModel {
    SCREEN_QUAD,
    MASKED_SCREEN,
    WORLD_PROJECTED,
    CUSTOM
}

class PostEffectChainBuilder {
    private val passes = mutableListOf<PostEffectPass>()
    private var output = PostEffectOutput.FINAL_SCREEN

    fun pass(name: String, fragment: ResourceLocation, block: PostEffectPassBuilder.() -> Unit = {}) = apply {
        passes += PostEffectPassBuilder(name, fragment).apply(block).build()
    }

    fun outputToFinalScreen() = apply { output = PostEffectOutput.FINAL_SCREEN }
    fun outputToBloomTarget() = apply { output = PostEffectOutput.BLOOM }
    fun outputToMaskTarget() = apply { output = PostEffectOutput.MASK }

    fun build(): PostEffectChain = PostEffectChain(passes.toList(), output)
}

class PostEffectPassBuilder(
    private val name: String,
    private val fragment: ResourceLocation
) {
    private val inputs = mutableListOf<PostEffectInput>()
    private val uniforms = mutableListOf<PostEffectUniform>()
    private val requiredCapabilities = linkedSetOf<RenderBackendCapability>()
    private val optionalCapabilities = linkedSetOf<RenderBackendCapability>()
    private var output = PostEffectOutput.TEMPORARY

    fun inputSceneColor(samplerName: String = "scene", optional: Boolean = false) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.SCENE_COLOR, optional)
        addCapability(RenderBackendCapability.SCENE_COLOR_COPY, optional)
    }

    fun inputSceneDepth(samplerName: String = "depth", optional: Boolean = true) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.SCENE_DEPTH, optional)
        addCapability(RenderBackendCapability.SCENE_DEPTH_READ, optional)
    }

    fun inputMask(samplerName: String = "mask", optional: Boolean = false) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.MASK, optional)
    }

    fun inputBrightColor(samplerName: String = "bright", optional: Boolean = false) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.BRIGHT_COLOR, optional)
        addCapability(RenderBackendCapability.SCENE_COLOR_COPY, optional)
    }

    fun inputCustomTexture(samplerName: String, optional: Boolean = false) = apply {
        inputs += PostEffectInput(samplerName, PostEffectInputSource.CUSTOM_TEXTURE, optional)
    }

    fun uniform(name: String, provider: (PostEffectInstance) -> PostEffectParamValue?) = apply {
        uniforms += PostEffectUniform(name, provider)
    }

    fun outputToTemporary() = apply { output = PostEffectOutput.TEMPORARY }
    fun outputToFinalScreen() = apply { output = PostEffectOutput.FINAL_SCREEN }
    fun outputToBloomTarget() = apply { output = PostEffectOutput.BLOOM }
    fun outputToMaskTarget() = apply { output = PostEffectOutput.MASK }

    fun require(capability: RenderBackendCapability) = apply { requiredCapabilities += capability }
    fun optional(capability: RenderBackendCapability) = apply { optionalCapabilities += capability }

    private fun addCapability(capability: RenderBackendCapability, optional: Boolean) {
        if (optional) optionalCapabilities += capability else requiredCapabilities += capability
    }

    fun build(): PostEffectPass {
        return PostEffectPass(
            name = name,
            fragment = fragment,
            inputs = inputs.toList(),
            output = output,
            uniforms = uniforms.toList(),
            requiredCapabilities = requiredCapabilities.toSet(),
            optionalCapabilities = optionalCapabilities.toSet()
        )
    }
}
