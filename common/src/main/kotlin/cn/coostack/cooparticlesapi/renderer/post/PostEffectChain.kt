package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation

/** Pipeline graph 编译后的内部 pass 列表。 */
internal data class PostEffectChain(
    val passes: List<PostEffectPass>,
    val output: PostEffectOutput = PostEffectOutput.FINAL_SCREEN
) {
    init {
        require(passes.isNotEmpty()) { "A post effect chain must contain at least one pass" }
        val passNames = passes.map(PostEffectPass::name)
        require(passNames.distinct().size == passNames.size) {
            "Post effect pass names must be unique: $passNames"
        }
        passes.forEach { pass ->
            require(pass.colorAttachmentCount > 0) {
                "Post effect pass ${pass.name} must declare at least one color attachment"
            }
            val explicitSlots = pass.inputs.mapNotNull(PostEffectInput::textureSlot)
            require(explicitSlots.distinct().size == explicitSlots.size) {
                "Post effect pass ${pass.name} has duplicate texture input slot(s): $explicitSlots"
            }
            pass.inputs.forEach { input ->
                require(input.textureSlot == null || input.textureSlot >= 0) {
                    "Post effect pass ${pass.name} input ${input.samplerName} has invalid texture slot ${input.textureSlot}"
                }
                if (input.source == PostEffectInputSource.PASS_OUTPUT) {
                    require(input.sourcePassName in passNames) {
                        "Post effect pass ${pass.name} references unknown input pass ${input.sourcePassName}"
                    }
                    require(input.sourcePassName != pass.name) {
                        "Post effect pass ${pass.name} cannot use itself as an input"
                    }
                    val sourcePass = passes.first { it.name == input.sourcePassName }
                    require(input.sourcePassAttachment in 0 until sourcePass.colorAttachmentCount) {
                        "Post effect pass ${pass.name} references missing attachment " +
                            "${input.sourcePassAttachment} from ${sourcePass.name}"
                    }
                }
                if (input.source == PostEffectInputSource.SCENE_RESOURCE) {
                    require(input.sourceResourceId != null) {
                        "Post effect pass ${pass.name} scene resource input ${input.samplerName} must declare a resource id"
                    }
                }
            }
        }
    }
}

internal data class PostEffectPass(
    val name: String,
    val vertex: ResourceLocation? = null,
    val fragment: ResourceLocation,
    val inputs: List<PostEffectInput> = emptyList(),
    val output: PostEffectOutput = PostEffectOutput.TEMPORARY,
    val uniforms: List<PostEffectUniform> = emptyList(),
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    val optionalCapabilities: Set<RenderBackendCapability> = emptySet(),
    val outputTargetId: ResourceLocation? = null,
    val outputTargetKey: String? = null,
    val colorAttachmentCount: Int = 1,
    val reuseOutputTarget: Boolean = false
)

internal data class PostEffectInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean = false,
    val sourcePassName: String? = null,
    val sourcePassAttachment: Int = 0,
    val sourceResourceId: ResourceLocation? = null,
    val sourceResourceAttachment: Int = 0,
    val sourceResourceChannel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
    val textureSlot: Int? = null
)

internal data class PostEffectUniform(
    val name: String,
    val provider: (PostEffectInstance) -> PostEffectParamValue?
)

internal enum class PostEffectInputSource {
    SCENE_COLOR,
    SCENE_DEPTH,
    MASK,
    BRIGHT_COLOR,
    CUSTOM_TEXTURE,
    PASS_OUTPUT,
    SCENE_RESOURCE
}

internal enum class PostEffectResourceChannel {
    COLOR,
    DEPTH
}

internal enum class PostEffectOutput {
    TEMPORARY,
    MASK,
    BLOOM,
    FINAL_SCREEN
}

internal enum class PostEffectModel {
    SCREEN_QUAD,
    MASKED_SCREEN,
    WORLD_PROJECTED,
    CUSTOM
}
