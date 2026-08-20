package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneResource
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import net.minecraft.resources.ResourceLocation

/**
 * 一次 frame-post 执行的汇总结果。
 *
 * 主要用于调试和测试：可以看到本帧为哪些 instance 建了 plan、执行了多少 pass、
 * 又因为缺输入或缺 backend 能力跳过了多少 pass。
 */
internal data class PostEffectExecutionSummary(
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
internal data class PostEffectExecutionPlan(
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
internal data class PostEffectExecutionStep(
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
internal data class PostEffectResolvedInput(
    val samplerName: String,
    val source: PostEffectInputSource,
    val optional: Boolean,
    val available: Boolean,
    val textureId: Int? = null,
    val resource: RenderSceneResource? = null,
    val producedBy: PostEffectOutput? = null,
    val producedByPassName: String? = null,
    val producedByPassAttachment: Int = 0,
    val sourceResourceId: ResourceLocation? = null,
    val sourceResourceAttachment: Int = 0,
    val sourceResourceChannel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
    val textureSlot: Int? = null,
    val expectedFormat: CooTextureFormat? = null,
    val minimumMipLevels: Int = 1
)

/**
 * 已解析的 pass 输出目标。
 *
 * [targetKey] 用于区分同一类 [PostEffectOutput] 下的多个实际 FBO，例如 bloom 多级 downsample/blur。
 * [scaleDivisor] 用于低分辨率 target，例如 bloom mip。
 */
internal data class PostEffectResolvedOutput(
    val output: PostEffectOutput,
    val targetId: ResourceLocation,
    val label: String,
    val textureId: Int? = null,
    val targetKey: String = label,
    val scaleDivisor: Int = 1,
    val colorAttachmentCount: Int = 1,
    val format: CooTextureFormat = CooTextureFormat.RGBA8,
    val mipLevels: Int = 1,
    val generateMipmaps: Boolean = false
)

/** world 节点捕获颜色 attachment 时传给执行后端的实际存储契约。 */
internal data class PostEffectAttachmentSpec(
    val format: CooTextureFormat = CooTextureFormat.RGBA8,
    val mipLevels: Int = 1
) {
    init {
        require(mipLevels > 0) { "A captured attachment must allocate at least one mip level" }
    }
}

/**
 * post pass 的执行后端接口。
 *
 * 默认 logging backend 只记录信息；客户端初始化时会安装 OpenGL backend。
 * 自定义 backend 可以用这个接口接管执行，但仍复用 [PostEffectFrameExecutor] 的 plan 构建、
 * 输入解析、uniform 解析和跳过逻辑。
 */
internal fun interface PostEffectExecutionBackend {
    /**
     * 执行 `PostEffectExecutionBackend` 定义的 `execute` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`execute(step = step)`。
     *
     * @param step 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun execute(step: PostEffectExecutionStep)
}

/** 可选接口：backend 可在每帧开始时清理临时状态或准备 scene copy。 */
internal interface PostEffectFramePreparationBackend {
    /**
     * 初始化或准备 `PostEffectFramePreparationBackend` 的 `prepareFrame` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`prepareFrame(context = context)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun prepareFrame(context: RenderFrameContext)

    /** 只使缓存的 SceneColor copy 失效，保留同帧 post chain。 */
    fun invalidateSceneColorCopy() = Unit

    /** 仅刷新场景颜色/深度副本；默认 backend 没有独立刷新语义时复用完整准备流程。 */
    fun refreshSceneFrame(context: RenderFrameContext) {
        prepareFrame(context)
    }
}

/** 可选接口：backend 可把延迟的透明前景绘制到当前最终合成目标。 */
internal fun interface PostEffectForegroundReplayBackend {
    /** 绑定最终 framebuffer 执行一次前景重放，并让后续 SceneColor 从重放结果继续合成。 */
    fun replayForeground(context: RenderFrameContext, render: () -> Unit): Boolean

    /** backend 是否具备前景重放能力；不代表本帧 external FBO 一定有效。 */
    fun supportsForegroundReplay(): Boolean = true
}

/** 可选接口：backend 可在 shader reload、客户端关闭或测试结束时释放 GL 资源。 */
internal interface PostEffectResourceBackend {
    /**
     * 释放 `PostEffectResourceBackend` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    fun release()
}

/** Pipeline world 节点把一个逻辑 FBO attachment 准备为后续 sampler 输入时使用。 */
internal interface PostEffectAttachmentPreparationBackend {
    /**
     * 尝试把后处理 attachment 临时挂到当前世界 framebuffer，并在同一次几何绘制中写入。
     *
     * 默认返回 `false`，调用方会恢复为世界绘制和离屏捕获各执行一次的兼容路径。
     */
    fun captureInlineAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean = false

    /**
     * 执行 `PostEffectAttachmentPreparationBackend` 定义的 `captureAttachment` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`captureAttachment(context = context, owner = owner, target = target, attachment = attachment, render = render)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param owner 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param render 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun captureAttachment(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachment: Int,
        render: () -> Unit
    ): Boolean

    /** 同一次 draw 捕获一个 FBO 的全部颜色 attachment。 */
    fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachmentCount: Int,
        render: () -> Unit
    ): Boolean {
        return (0 until attachmentCount).all { attachment ->
            captureAttachment(context, owner, target, attachment, render)
        }
    }

    /** 同一次 draw 按完整资源契约捕获一个 FBO 的全部颜色 attachment。 */
    fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean {
        return captureAttachments(context, owner, target, attachments.size, render)
    }

    /**
     * 执行 `PostEffectAttachmentPreparationBackend` 定义的 `hasAttachment` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`hasAttachment(target = target, attachment = attachment)`。
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun hasAttachment(target: ResourceLocation, attachment: Int): Boolean
}

/** 无 OpenGL 环境下的安全默认 backend，便于测试和服务端侧加载。 */
internal object LoggingPostEffectExecutionBackend : PostEffectExecutionBackend {
    /**
     * 执行 `LoggingPostEffectExecutionBackend` 定义的 `execute` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`execute(step = step)`。
     *
     * @param step 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
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
 *
 * 这个对象不直接调用 OpenGL。它替代的是“每个效果自己写一套执行顺序和输入检查”的代码。
 */
internal object PostEffectFrameExecutor {
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

    /** 在同一帧 final pass 后仅使 scene color/depth copy 失效，不清空已生成的 pass 状态。 */
    fun refreshSceneFrame(context: RenderFrameContext) {
        (backend as? PostEffectFramePreparationBackend)?.refreshSceneFrame(context)
    }
    /** 当前 backend 是否支持在最终合成目标上进行受控前景重放。 */
    fun supportsForegroundReplay(): Boolean {
        return (backend as? PostEffectForegroundReplayBackend)?.supportsForegroundReplay() == true
    }

    /** 只失效缓存 SceneColor，不清除同帧 chain framebuffer。 */
    fun invalidateSceneColorCopy() {
        (backend as? PostEffectFramePreparationBackend)?.invalidateSceneColorCopy()
    }

    /** 在最终合成目标上执行延迟前景；不支持该能力的 backend 返回 `false`。 */
    fun replayForeground(context: RenderFrameContext, render: () -> Unit): Boolean {
        val replayBackend = backend as? PostEffectForegroundReplayBackend ?: return false
        return replayBackend.replayForeground(context, render)
    }

    /** 释放 backend 持有的临时纹理、FBO、shader program 等资源。 */
    fun releaseBackendResources() {
        (backend as? PostEffectResourceBackend)?.release()
    }

    /** 尝试在当前世界 framebuffer 上以内联 MRT 方式捕获 Pipeline attachment。 */
    internal fun captureInlineAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean {
        val attachmentBackend = backend as? PostEffectAttachmentPreparationBackend ?: return false
        return attachmentBackend.captureInlineAttachments(context, owner, target, attachments, render)
    }

    /**
     * 执行 `PostEffectFrameExecutor` 定义的 `captureAttachment` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`captureAttachment(context = context, owner = owner, target = target, attachment = attachment, render = render)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param owner 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachment 数量或从零开始的索引值，具体上限由当前资源配置决定
     *
     * @param render 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    internal fun captureAttachment(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachment: Int,
        render: () -> Unit
    ): Boolean {
        val attachmentBackend = backend as? PostEffectAttachmentPreparationBackend ?: return false
        return attachmentBackend.captureAttachment(context, owner, target, attachment, render)
    }

    /**
     * 执行 `PostEffectFrameExecutor` 定义的 `captureAttachments` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`captureAttachments(context = context, owner = owner, target = target, attachmentCount = attachmentCount, render = render)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param owner 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param target 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param attachmentCount 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param render 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    internal fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachmentCount: Int,
        render: () -> Unit
    ): Boolean {
        val attachmentBackend = backend as? PostEffectAttachmentPreparationBackend ?: return false
        return attachmentBackend.captureAttachments(context, owner, target, attachmentCount, render)
    }

    /** 按 Pipeline 声明的格式和 mip 层数捕获同一个 FBO 的全部颜色 attachment。 */
    internal fun captureAttachments(
        context: RenderFrameContext,
        owner: String,
        target: ResourceLocation,
        attachments: List<PostEffectAttachmentSpec>,
        render: () -> Unit
    ): Boolean {
        val attachmentBackend = backend as? PostEffectAttachmentPreparationBackend ?: return false
        return attachmentBackend.captureAttachments(context, owner, target, attachments, render)
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
            val output = resolveOutput(context, pass, expandedPass.targetKey, expandedPass.scaleDivisor)
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
        return orderPasses(instance.type.chain.passes).map(::ExpandedPostEffectPass)
    }

    private fun resolveInput(
        context: RenderFrameContext,
        instance: PostEffectInstance,
        input: PostEffectInput,
        producedOutputs: Set<PostEffectOutput>,
        producedPasses: Set<String>
    ): PostEffectResolvedInput {
        val resolved = when (input.source) {
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
            PostEffectInputSource.SCENE_DEPTH_NO_HAND -> {
                val resource = context.sceneResources[RenderSceneTargets.SCENE_DEPTH_NO_HAND]
                val textureId = resource?.depthTextureId
                val capabilityAvailable = RenderBackendCapability.SCENE_DEPTH_READ in context.backend.capabilities
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = capabilityAvailable && textureId != null,
                    textureId = textureId,
                    resource = resource,
                    textureSlot = input.textureSlot
                )
            }
            PostEffectInputSource.TERRAIN_DEPTH -> {
                val resource = context.sceneResources[RenderSceneTargets.TERRAIN_DEPTH]
                val textureId = resource?.depthTextureId
                val capabilityAvailable = RenderBackendCapability.TERRAIN_DEPTH_READ in context.backend.capabilities
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
            PostEffectInputSource.TERRAIN_OPAQUE_DEPTH -> resolveTerrainSnapshotInput(
                context,
                input,
                RenderSceneTargets.TERRAIN_OPAQUE_DEPTH
            )
            PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_BEFORE -> resolveTerrainSnapshotInput(
                context,
                input,
                RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_BEFORE
            )
            PostEffectInputSource.TERRAIN_TRANSLUCENT_DEPTH_AFTER -> resolveTerrainSnapshotInput(
                context,
                input,
                RenderSceneTargets.TERRAIN_TRANSLUCENT_DEPTH_AFTER
            )
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
                    producedByPassAttachment = input.sourcePassAttachment,
                    textureSlot = input.textureSlot
                )
            }
            PostEffectInputSource.SCENE_RESOURCE -> {
                val resourceId = input.sourceResourceId
                val resource = resourceId?.let { context.sceneResources[it] }
                val textureId = when (input.sourceResourceChannel) {
                    PostEffectResourceChannel.COLOR -> resource?.colorTextureId(input.sourceResourceAttachment)
                    PostEffectResourceChannel.DEPTH -> resource?.depthTextureId
                }
                PostEffectResolvedInput(
                    samplerName = input.samplerName,
                    source = input.source,
                    optional = input.optional,
                    available = textureId != null || resource != null ||
                        resourceId?.let { hasCapturedAttachment(it, input.sourceResourceAttachment) } == true ||
                        input.optional,
                    textureId = textureId,
                    resource = resource,
                    sourceResourceId = resourceId,
                    sourceResourceAttachment = input.sourceResourceAttachment,
                    sourceResourceChannel = input.sourceResourceChannel,
                    textureSlot = input.textureSlot
                )
            }
        }
        return resolved.copy(
            expectedFormat = input.expectedFormat,
            minimumMipLevels = input.minimumMipLevels
        )
    }

    private fun resolveTerrainSnapshotInput(
        context: RenderFrameContext,
        input: PostEffectInput,
        target: ResourceLocation
    ): PostEffectResolvedInput {
        val resource = context.sceneResources[target]
        val textureId = resource?.depthTextureId
        val capabilityAvailable = RenderBackendCapability.TERRAIN_DEPTH_READ in context.backend.capabilities
        return PostEffectResolvedInput(
            samplerName = input.samplerName,
            source = input.source,
            optional = input.optional,
            available = capabilityAvailable && textureId != null,
            textureId = textureId,
            resource = resource,
            textureSlot = input.textureSlot
        )
    }
    private fun hasCapturedAttachment(target: ResourceLocation, attachment: Int): Boolean {
        val attachmentBackend = backend as? PostEffectAttachmentPreparationBackend ?: return false
        return attachmentBackend.hasAttachment(target, attachment)
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
        pass: PostEffectPass,
        targetKey: String? = null,
        scaleDivisor: Int = 1
    ): PostEffectResolvedOutput {
        val output = pass.output
        val targetId = pass.outputTargetId ?: targetIdFor(output)
        val resource = context.sceneResources[targetId]
        val label = targetKey ?: pass.outputTargetKey ?: resource?.label ?: output.name.lowercase()
        return PostEffectResolvedOutput(
            output = output,
            targetId = targetId,
            label = label,
            textureId = resource?.colorTextureId,
            targetKey = targetKey ?: label,
            scaleDivisor = scaleDivisor.coerceAtLeast(1),
            colorAttachmentCount = pass.colorAttachmentCount,
            format = pass.outputFormat,
            mipLevels = pass.mipLevels,
            generateMipmaps = pass.generateMipmaps
        )
    }

    /** 解析 pass uniform；批次已冻结的值优先于原始 provider。 */
    private fun resolveUniforms(
        pass: PostEffectPass,
        instance: PostEffectInstance
    ): Map<String, PostEffectParamValue> {
        return pass.uniforms.mapNotNull { uniform ->
            val key = pass.name to uniform.name
            val value = if (key in instance.uniformOverrides) {
                instance.uniformOverrides[key]
            } else {
                uniform.provider(instance)
            }
            value?.let { uniform.name to it }
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
