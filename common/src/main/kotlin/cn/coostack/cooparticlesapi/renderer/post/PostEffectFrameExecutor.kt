package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import net.minecraft.resources.ResourceLocation

data class PostEffectExecutionSummary(
    val plans: List<PostEffectExecutionPlan>,
    val executedPasses: Int,
    val skippedPasses: Int
)

data class PostEffectExecutionPlan(
    val instance: PostEffectInstance,
    val steps: List<PostEffectExecutionStep>
)

data class PostEffectExecutionStep(
    val context: RenderFrameContext,
    val instance: PostEffectInstance,
    val pass: PostEffectPass,
    val passIndex: Int,
    val inputs: List<PostEffectResolvedInput>,
    val uniforms: Map<String, PostEffectParamValue>,
    val output: PostEffectResolvedOutput,
    val skippedReason: String? = null
) {
    val executable: Boolean get() = skippedReason == null
}

data class PostEffectResolvedInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean,
    val available: Boolean,
    val textureId: Int? = null,
    val resource: RenderSceneResource? = null,
    val producedBy: PostEffectOutput? = null
)

data class PostEffectResolvedOutput(
    val output: PostEffectOutput,
    val targetId: ResourceLocation,
    val label: String,
    val textureId: Int? = null,
    val targetKey: String = label,
    val scaleDivisor: Int = 1
)

fun interface PostEffectExecutionBackend {
    fun execute(step: PostEffectExecutionStep)
}

interface PostEffectFramePreparationBackend {
    fun prepareFrame(context: RenderFrameContext)
}

interface PostEffectResourceBackend {
    fun release()
}

object LoggingPostEffectExecutionBackend : PostEffectExecutionBackend {
    override fun execute(step: PostEffectExecutionStep) {
        CooParticlesConstants.logger.debug(
            "Executing post effect type={} id={} pass={} output={} inputs={} uniforms={}",
            step.instance.type.id,
            step.instance.instanceId,
            step.pass.name,
            step.output.output,
            step.inputs.map { "${it.samplerName}:${it.source}" },
            step.uniforms.keys
        )
    }
}

object PostEffectFrameExecutor {
    private const val MAX_BLOOM_MIP_LEVELS = 6
    private const val MAX_BLOOM_ITERATIONS = 8

    private val builtinBloomId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom")
    private val bloomDownsampleFragment =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_downsample.fsh")
    private val bloomUpsampleFragment =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_upsample.fsh")

    private var backend: PostEffectExecutionBackend = LoggingPostEffectExecutionBackend

    fun installBackend(executionBackend: PostEffectExecutionBackend) {
        backend = executionBackend
    }

    fun resetBackend() {
        backend = LoggingPostEffectExecutionBackend
    }

    fun prepareFrame(context: RenderFrameContext) {
        (backend as? PostEffectFramePreparationBackend)?.prepareFrame(context)
    }

    fun releaseBackendResources() {
        (backend as? PostEffectResourceBackend)?.release()
    }

    fun execute(context: RenderFrameContext, instances: List<PostEffectInstance>): PostEffectExecutionSummary {
        val plans = instances.map { buildPlan(context, it) }
        var executedPasses = 0
        var skippedPasses = 0
        plans.forEach { plan ->
            plan.steps.forEach { step ->
                if (step.executable) {
                    backend.execute(step)
                    executedPasses++
                } else {
                    skippedPasses++
                    CooParticlesConstants.logger.debug(
                        "Skipping post effect type={} id={} pass={} because {}",
                        step.instance.type.id,
                        step.instance.instanceId,
                        step.pass.name,
                        step.skippedReason
                    )
                }
            }
        }
        return PostEffectExecutionSummary(plans, executedPasses, skippedPasses)
    }

    fun buildPlan(context: RenderFrameContext, instance: PostEffectInstance): PostEffectExecutionPlan {
        val producedOutputs = LinkedHashSet<PostEffectOutput>()
        val expandedPasses = expandPasses(instance)
        val steps = expandedPasses.mapIndexed { index, expandedPass ->
            val pass = expandedPass.pass
            val inputs = pass.inputs.map { input ->
                resolveInput(context, instance, input, producedOutputs)
            }
            val missingRequiredInputs = inputs
                .filter { !it.optional && !it.available }
                .map { it.samplerName }
            val missingCapabilities = pass.requiredCapabilities - context.backend.capabilities
            val output = resolveOutput(context, pass.output, expandedPass.targetKey, expandedPass.scaleDivisor)
            val skippedReason = if (missingCapabilities.isNotEmpty()) {
                "missing required capability(s): ${missingCapabilities.joinToString()}"
            } else if (missingRequiredInputs.isEmpty()) {
                producedOutputs += pass.output
                null
            } else {
                "missing required input(s): ${missingRequiredInputs.joinToString()}"
            }
            PostEffectExecutionStep(
                context = context,
                instance = instance,
                pass = pass,
                passIndex = index,
                inputs = inputs,
                uniforms = resolveUniforms(pass, instance),
                output = output,
                skippedReason = skippedReason
            )
        }
        return PostEffectExecutionPlan(instance, steps)
    }

    private fun expandPasses(instance: PostEffectInstance): List<ExpandedPostEffectPass> {
        if (instance.type.id != builtinBloomId) {
            return instance.type.chain.passes.map { ExpandedPostEffectPass(it) }
        }

        val passes = instance.type.chain.passes
        val brightExtract = passes.firstOrNull { it.name == "bright_extract" }
        val blurHorizontal = passes.firstOrNull { it.name == "blur_horizontal" }
        val blurVertical = passes.firstOrNull { it.name == "blur_vertical" }
        val composite = passes.firstOrNull { it.name == "composite" }
        if (brightExtract == null || blurHorizontal == null || blurVertical == null || composite == null) {
            return passes.map { ExpandedPostEffectPass(it) }
        }

        val mipLevels = instance.intParam("mipLevels")
            ?: instance.intParam("mipLevel")
            ?: 4
        val levels = mipLevels.coerceIn(1, MAX_BLOOM_MIP_LEVELS)
        val iterations = (instance.intParam("iterations") ?: 1).coerceIn(1, MAX_BLOOM_ITERATIONS)
        val expanded = mutableListOf<ExpandedPostEffectPass>()

        expanded += ExpandedPostEffectPass(
            pass = brightExtract,
            targetKey = "bloom/bright/full",
            scaleDivisor = 1
        )

        for (level in 1..levels) {
            val scaleDivisor = 1 shl level
            expanded += ExpandedPostEffectPass(
                pass = bloomGeneratedPass("downsample_$level", bloomDownsampleFragment),
                targetKey = "bloom/downsample/$level",
                scaleDivisor = scaleDivisor
            )
            repeat(iterations) { iteration ->
                expanded += ExpandedPostEffectPass(
                    pass = bloomSingleIterationBlurPass(
                        blurHorizontal,
                        "blur_horizontal_l${level}_i${iteration + 1}"
                    ),
                    targetKey = "bloom/blur_h/$level/${iteration + 1}",
                    scaleDivisor = scaleDivisor
                )
                expanded += ExpandedPostEffectPass(
                    pass = bloomSingleIterationBlurPass(
                        blurVertical,
                        "blur_vertical_l${level}_i${iteration + 1}"
                    ),
                    targetKey = "bloom/blur_v/$level/${iteration + 1}",
                    scaleDivisor = scaleDivisor
                )
            }
        }

        for (level in levels downTo 1) {
            val targetScale = if (level == 1) 1 else 1 shl (level - 1)
            expanded += ExpandedPostEffectPass(
                pass = bloomGeneratedPass("upsample_$level", bloomUpsampleFragment),
                targetKey = "bloom/upsample/$level",
                scaleDivisor = targetScale
            )
        }

        expanded += ExpandedPostEffectPass(composite)
        return expanded
    }

    private fun bloomGeneratedPass(name: String, fragment: ResourceLocation): PostEffectPass {
        return PostEffectPass(
            name = name,
            fragment = fragment,
            inputs = listOf(PostEffectInput("bright", PostEffectInputSource.BRIGHT_COLOR)),
            output = PostEffectOutput.BLOOM
        )
    }

    private fun bloomSingleIterationBlurPass(pass: PostEffectPass, name: String): PostEffectPass {
        val uniforms = pass.uniforms.filterNot { it.name == "iterations" } +
            PostEffectUniform("iterations") { PostEffectParamValue.IntValue(1) }
        return pass.copy(name = name, uniforms = uniforms)
    }

    private fun resolveInput(
        context: RenderFrameContext,
        instance: PostEffectInstance,
        input: PostEffectInput,
        producedOutputs: Set<PostEffectOutput>
    ): PostEffectResolvedInput {
        return when (input.source) {
            PostEffectInputSource.SCENE_COLOR -> {
                val resource = context.sceneResources[RenderSceneTargets.SCENE_COLOR]
                val textureId = context.sceneColorTextureId ?: resource?.colorTextureId
                val capabilityAvailable = RenderBackendCapability.SCENE_COLOR_COPY in context.backend.capabilities
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = capabilityAvailable && (textureId != null || resource != null),
                    textureId = textureId,
                    resource = resource
                )
            }
            PostEffectInputSource.SCENE_DEPTH -> {
                val resource = context.sceneResources[RenderSceneTargets.SCENE_DEPTH]
                val textureId = context.sceneDepthTextureId ?: resource?.depthTextureId
                val capabilityAvailable = RenderBackendCapability.SCENE_DEPTH_READ in context.backend.capabilities
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = capabilityAvailable && (textureId != null || resource != null),
                    textureId = textureId,
                    resource = resource
                )
            }
            PostEffectInputSource.MASK -> resolveProducedInput(context, input, PostEffectOutput.MASK, producedOutputs)
            PostEffectInputSource.BRIGHT_COLOR -> resolveProducedInput(context, input, PostEffectOutput.BLOOM, producedOutputs)
            PostEffectInputSource.CUSTOM_TEXTURE -> {
                val resourceParam = instance.params[input.samplerName] as? PostEffectParamValue.Resource
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = resourceParam != null || input.optional
                )
            }
        }
    }

    private fun resolveProducedInput(
        context: RenderFrameContext,
        input: PostEffectInput,
        producedOutput: PostEffectOutput,
        producedOutputs: Set<PostEffectOutput>
    ): PostEffectResolvedInput {
        val targetId = targetIdFor(producedOutput)
        val resource = context.sceneResources[targetId]
        val textureId = resource?.colorTextureId
        return PostEffectResolvedInput(
            samplerName = input.samplerName,
            source = input.source,
            optional = input.optional,
            available = producedOutput in producedOutputs || textureId != null || resource != null,
            textureId = textureId,
            resource = resource,
            producedBy = producedOutput
        )
    }

    private fun resolveOutput(
        context: RenderFrameContext,
        output: PostEffectOutput,
        targetKey: String? = null,
        scaleDivisor: Int = 1
    ): PostEffectResolvedOutput {
        val targetId = targetIdFor(output)
        val resource = context.sceneResources[targetId]
        val label = targetKey ?: resource?.label ?: output.name.lowercase()
        return PostEffectResolvedOutput(
            output = output,
            targetId = targetId,
            label = label,
            textureId = resource?.colorTextureId,
            targetKey = targetKey ?: label,
            scaleDivisor = scaleDivisor.coerceAtLeast(1)
        )
    }

    private fun resolveUniforms(
        pass: PostEffectPass,
        instance: PostEffectInstance
    ): Map<String, PostEffectParamValue> {
        return pass.uniforms.mapNotNull { uniform ->
            uniform.provider(instance)?.let { uniform.name to it }
        }.toMap()
    }

    private fun targetIdFor(output: PostEffectOutput): ResourceLocation {
        return when (output) {
            PostEffectOutput.TEMPORARY -> RenderSceneTargets.TEMPORARY
            PostEffectOutput.MASK -> RenderSceneTargets.MASK
            PostEffectOutput.BLOOM -> RenderSceneTargets.BLOOM
            PostEffectOutput.FINAL_SCREEN -> RenderSceneTargets.POST
        }
    }

    private fun PostEffectInstance.intParam(name: String): Int? {
        return when (val value = params[name]) {
            is PostEffectParamValue.IntValue -> value.value
            is PostEffectParamValue.LongValue -> value.value.toInt()
            is PostEffectParamValue.FloatValue -> value.value.toInt()
            is PostEffectParamValue.DoubleValue -> value.value.toInt()
            else -> null
        }
    }

    private data class ExpandedPostEffectPass(
        val pass: PostEffectPass,
        val targetKey: String? = null,
        val scaleDivisor: Int = 1
    )
}
