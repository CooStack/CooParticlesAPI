package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import net.minecraft.resources.ResourceLocation

/**
 * 一次 frame-post 执行的汇总结果。
 *
 * 主要用于调试和测试：可以看到本帧为哪些 instance 建了 plan、执行了多少 pass、
 * 又因为缺输入或缺 backend 能力跳过了多少 pass。
 */
data class PostEffectExecutionSummary(
    val plans: List<PostEffectExecutionPlan>,
    val executedPasses: Int,
    val skippedPasses: Int
)

/**
 * 某个 [PostEffectInstance] 在当前帧的执行计划。
 *
 * 自定义 post 出问题时，优先检查这里的 [steps]：
 * pass 是否按预期排序、输入是否 available、uniform 是否解析到了值。
 */
data class PostEffectExecutionPlan(
    val instance: PostEffectInstance,
    val steps: List<PostEffectExecutionStep>
)

/**
 * 单个 pass 在当前帧、当前 backend 下的可执行步骤。
 *
 * 它是静态 [PostEffectPass] 和动态运行环境之间的桥：
 *
 * - [inputs] 已经把 `SCENE_COLOR/PASS_OUTPUT/CUSTOM_TEXTURE` 等来源解析成“是否可用”
 * - [uniforms] 已经从 [PostEffectInstance.params] 计算完成
 * - [output] 已经解析出 target key、缩放等级和逻辑输出
 * - [skippedReason] 非空时 backend 不会执行这个 pass
 */
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
    /** 该 pass 本帧是否应交给 backend 执行。 */
    val executable: Boolean get() = skippedReason == null
}

/**
 * 已解析的 sampler 输入。
 *
 * 这不是用户声明的 [PostEffectInput]，而是 executor 结合当前 frame context 后得到的运行时结果。
 * [textureId] 可能仍为空，因为某些输入需要 backend 在执行时生成或读取，例如 scene color copy、
 * binding mask 或上游 pass output。
 */
data class PostEffectResolvedInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean,
    val available: Boolean,
    val textureId: Int? = null,
    val resource: RenderSceneResource? = null,
    val producedBy: PostEffectOutput? = null,
    val producedByPassName: String? = null,
    val sourceResourceId: ResourceLocation? = null,
    val sourceResourceChannel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
    val textureSlot: Int? = null
)

/**
 * 已解析的 pass 输出目标。
 *
 * [targetKey] 用于区分同一类 [PostEffectOutput] 下的多个实际 FBO，例如 bloom 多级 downsample/blur。
 * [scaleDivisor] 用于低分辨率 target，例如 bloom mip。
 */
data class PostEffectResolvedOutput(
    val output: PostEffectOutput,
    val targetId: ResourceLocation,
    val label: String,
    val textureId: Int? = null,
    val targetKey: String = label,
    val scaleDivisor: Int = 1
)

/**
 * post pass 的执行后端接口。
 *
 * 默认 logging backend 只记录信息；客户端初始化时会安装 OpenGL backend。
 * 自定义 backend 可以用这个接口接管执行，但仍复用 [PostEffectFrameExecutor] 的 plan 构建、
 * 输入解析、uniform 解析和跳过逻辑。
 */
fun interface PostEffectExecutionBackend {
    fun execute(step: PostEffectExecutionStep)
}

/** 可选接口：backend 可在每帧开始时清理临时状态或准备 scene copy。 */
interface PostEffectFramePreparationBackend {
    fun prepareFrame(context: RenderFrameContext)
}

/** 可选接口：backend 可在 shader reload、客户端关闭或测试结束时释放 GL 资源。 */
interface PostEffectResourceBackend {
    fun release()
}

/** 无 OpenGL 环境下的安全默认 backend，便于测试和服务端侧加载。 */
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

/**
 * post effect 每帧执行规划器。
 *
 * 它负责把用户声明的 [PostEffectChain] 变成 backend 可执行的步骤，具体包括：
 *
 * - 对 [PostEffectInputSource.PASS_OUTPUT] 做拓扑排序，支持 A/C/E -> B/D 的图连接
 * - 根据当前 [RenderFrameContext] 判断 scene color、scene depth、scene resource 是否可用
 * - 根据 [PostEffectInstance] 解析普通 uniform
 * - 为输出 target 决定 label、targetKey、scaleDivisor
 * - 对内置 bloom 展开多级 downsample / blur / upsample
 *
 * 这个对象不直接调用 OpenGL。它替代的是“每个效果自己写一套执行顺序和输入检查”的代码。
 */
object PostEffectFrameExecutor {
    private const val MAX_BLOOM_MIP_LEVELS = 6
    private const val MAX_BLOOM_ITERATIONS = 8

    private val builtinBloomId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom")
    private val bloomDownsampleFragment =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_downsample.fsh")
    private val bloomUpsampleFragment =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/bloom_upsample.fsh")

    private var backend: PostEffectExecutionBackend = LoggingPostEffectExecutionBackend

    /**
     * 安装真实执行后端。
     *
     * 客户端 OpenGL 初始化时调用；测试或无渲染环境通常保持默认 logging backend。
     */
    fun installBackend(executionBackend: PostEffectExecutionBackend) {
        backend = executionBackend
    }

    /** 恢复到无 OpenGL 副作用的 logging backend，常用于测试清理或客户端关闭后的状态复位。 */
    fun resetBackend() {
        backend = LoggingPostEffectExecutionBackend
    }

    /**
     * 每帧 post 执行前的准备入口。
     *
     * 如果当前 backend 实现 [PostEffectFramePreparationBackend]，这里会让它复制 scene color/depth
     * 或清理上一帧临时 target。调用方不需要自己判断 backend 类型。
     */
    fun prepareFrame(context: RenderFrameContext) {
        (backend as? PostEffectFramePreparationBackend)?.prepareFrame(context)
    }

    /** 释放 backend 持有的临时纹理、FBO、shader program 等资源。 */
    fun releaseBackendResources() {
        (backend as? PostEffectResourceBackend)?.release()
    }

    /**
     * 执行当前帧的一组 post 实例。
     *
     * 这个函数会先为每个 instance 构建 plan，然后只把可执行 step 交给 backend。
     * 缺少必需输入或能力的 pass 会进入 [PostEffectExecutionSummary.skippedPasses]。
     */
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

    /**
     * 只构建执行计划，不执行 GL。
     *
     * 适合单元测试、调试复杂图连接、检查 sampler slot 和 optional 输入是否按预期解析。
     * 例如 `A -> B`、`C -> B`、`B -> D` 这种图会在这里完成拓扑排序。
     */
    fun buildPlan(context: RenderFrameContext, instance: PostEffectInstance): PostEffectExecutionPlan {
        val producedOutputs = LinkedHashSet<PostEffectOutput>()
        val producedPasses = LinkedHashSet<String>()
        val expandedPasses = expandPasses(instance)
        val steps = expandedPasses.mapIndexed { index, expandedPass ->
            val pass = expandedPass.pass
            val inputs = pass.inputs.map { input ->
                resolveInput(context, instance, input, producedOutputs, producedPasses)
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
                producedPasses += pass.name
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
            return orderPasses(instance.type.chain.passes).map { ExpandedPostEffectPass(it) }
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

        var previousPassName = brightExtract.name
        val perLevelFinalPass = mutableMapOf<Int, String>()

        for (level in 1..levels) {
            val scaleDivisor = 1 shl level
            val downsampleName = "downsample_$level"
            expanded += ExpandedPostEffectPass(
                pass = bloomDownsamplePass(downsampleName, sourcePassName = previousPassName),
                targetKey = "bloom/downsample/$level",
                scaleDivisor = scaleDivisor
            )
            previousPassName = downsampleName

            repeat(iterations) { iteration ->
                val blurHName = "blur_horizontal_l${level}_i${iteration + 1}"
                val blurVName = "blur_vertical_l${level}_i${iteration + 1}"
                expanded += ExpandedPostEffectPass(
                    pass = bloomSingleIterationBlurPass(
                        blurHorizontal,
                        blurHName,
                        sourcePassName = previousPassName
                    ),
                    targetKey = "bloom/blur_h/$level/${iteration + 1}",
                    scaleDivisor = scaleDivisor
                )
                expanded += ExpandedPostEffectPass(
                    pass = bloomSingleIterationBlurPass(
                        blurVertical,
                        blurVName,
                        sourcePassName = blurHName
                    ),
                    targetKey = "bloom/blur_v/$level/${iteration + 1}",
                    scaleDivisor = scaleDivisor
                )
                previousPassName = blurVName
            }
            perLevelFinalPass[level] = previousPassName
        }

        for (level in levels downTo 1) {
            val targetScale = if (level == 1) 1 else 1 shl (level - 1)
            val upsampleName = "upsample_$level"
            // Read the lower (coarser) mip just produced. For the deepest level we read the
            // last blur of that level; for higher levels we read the previous upsample (one mip
            // smaller) so the chain telescopes back up to full resolution.
            val sourcePass = if (level == levels) {
                perLevelFinalPass.getValue(level)
            } else {
                "upsample_${level + 1}"
            }
            expanded += ExpandedPostEffectPass(
                pass = bloomUpsamplePass(upsampleName, sourcePassName = sourcePass),
                targetKey = "bloom/upsample/$level",
                scaleDivisor = targetScale
            )
            previousPassName = upsampleName
        }

        // Composite reads the full-size upsample explicitly so the resolution does not depend on
        // "last write to BLOOM target" semantics. The OpenGL backend regenerates mipmaps on the
        // upsample_1 texture so bloom_composite's textureLod chain is real.
        expanded += ExpandedPostEffectPass(
            pass = withBrightSourcePass(composite, sourcePassName = previousPassName)
        )
        return expanded
    }

    private fun bloomDownsamplePass(name: String, sourcePassName: String): PostEffectPass {
        return PostEffectPass(
            name = name,
            fragment = bloomDownsampleFragment,
            inputs = listOf(
                PostEffectInput(
                    samplerName = "bright",
                    source = PostEffectInputSource.PASS_OUTPUT,
                    sourcePassName = sourcePassName
                )
            ),
            output = PostEffectOutput.BLOOM
        )
    }

    private fun bloomUpsamplePass(name: String, sourcePassName: String): PostEffectPass {
        return PostEffectPass(
            name = name,
            fragment = bloomUpsampleFragment,
            inputs = listOf(
                PostEffectInput(
                    samplerName = "bright",
                    source = PostEffectInputSource.PASS_OUTPUT,
                    sourcePassName = sourcePassName
                )
            ),
            output = PostEffectOutput.BLOOM
        )
    }

    private fun bloomSingleIterationBlurPass(
        pass: PostEffectPass,
        name: String,
        sourcePassName: String
    ): PostEffectPass {
        val rewiredInputs = pass.inputs.map { input ->
            if (input.source == PostEffectInputSource.BRIGHT_COLOR) {
                input.copy(
                    source = PostEffectInputSource.PASS_OUTPUT,
                    sourcePassName = sourcePassName
                )
            } else {
                input
            }
        }
        val uniforms = pass.uniforms.filterNot { it.name == "iterations" } +
            PostEffectUniform("iterations") { PostEffectParamValue.IntValue(1) }
        return pass.copy(name = name, inputs = rewiredInputs, uniforms = uniforms)
    }

    private fun withBrightSourcePass(pass: PostEffectPass, sourcePassName: String): PostEffectPass {
        val rewiredInputs = pass.inputs.map { input ->
            if (input.source == PostEffectInputSource.BRIGHT_COLOR) {
                input.copy(
                    source = PostEffectInputSource.PASS_OUTPUT,
                    sourcePassName = sourcePassName
                )
            } else {
                input
            }
        }
        return pass.copy(inputs = rewiredInputs)
    }

    private fun resolveInput(
        context: RenderFrameContext,
        instance: PostEffectInstance,
        input: PostEffectInput,
        producedOutputs: Set<PostEffectOutput>,
        producedPasses: Set<String>
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
                    resource = resource,
                    textureSlot = input.textureSlot
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
                    resource = resource,
                    textureSlot = input.textureSlot
                )
            }
            PostEffectInputSource.MASK -> resolveProducedInput(context, input, PostEffectOutput.MASK, producedOutputs)
            PostEffectInputSource.BRIGHT_COLOR -> resolveProducedInput(context, input, PostEffectOutput.BLOOM, producedOutputs)
            PostEffectInputSource.CUSTOM_TEXTURE -> {
                val textureParam = instance.params[input.samplerName]
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = textureParam is PostEffectParamValue.ResourceValue ||
                        textureParam is PostEffectParamValue.IntValue ||
                        textureParam is PostEffectParamValue.LongValue ||
                        input.optional,
                    textureSlot = input.textureSlot
                )
            }
            PostEffectInputSource.PASS_OUTPUT -> {
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = input.sourcePassName in producedPasses || input.optional,
                    producedByPassName = input.sourcePassName,
                    textureSlot = input.textureSlot
                )
            }
            PostEffectInputSource.SCENE_RESOURCE -> {
                val resourceId = input.sourceResourceId
                val resource = resourceId?.let { context.sceneResources[it] }
                val textureId = when (input.sourceResourceChannel) {
                    PostEffectResourceChannel.COLOR -> resource?.colorTextureId
                    PostEffectResourceChannel.DEPTH -> resource?.depthTextureId
                }
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = textureId != null || resource != null || input.optional,
                    textureId = textureId,
                    resource = resource,
                    sourceResourceId = resourceId,
                    sourceResourceChannel = input.sourceResourceChannel,
                    textureSlot = input.textureSlot
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
            producedBy = producedOutput,
            textureSlot = input.textureSlot
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

    private fun orderPasses(passes: List<PostEffectPass>): List<PostEffectPass> {
        if (passes.size <= 1) {
            return passes
        }
        val passByName = passes.associateBy { it.name }
        val dependencies = passes.associate { pass ->
            pass.name to pass.inputs
                .filter { it.source == PostEffectInputSource.PASS_OUTPUT }
                .mapNotNull { it.sourcePassName }
                .toMutableSet()
        }.toMutableMap()
        val dependents = linkedMapOf<String, MutableList<String>>()
        dependencies.forEach { (passName, inputPasses) ->
            inputPasses.forEach { inputPass ->
                if (inputPass in passByName) {
                    dependents.getOrPut(inputPass) { mutableListOf() } += passName
                }
            }
        }
        val ready = ArrayDeque(passes.map { it.name }.filter { dependencies.getValue(it).isEmpty() })
        val orderedNames = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val passName = ready.removeFirst()
            orderedNames += passName
            dependents[passName].orEmpty().forEach { dependent ->
                val remaining = dependencies.getValue(dependent)
                remaining -= passName
                if (remaining.isEmpty()) {
                    ready.addLast(dependent)
                }
            }
        }
        require(orderedNames.size == passes.size) {
            "Post effect graph contains a cycle: ${passes.map { it.name }}"
        }
        return orderedNames.map { passByName.getValue(it) }
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
