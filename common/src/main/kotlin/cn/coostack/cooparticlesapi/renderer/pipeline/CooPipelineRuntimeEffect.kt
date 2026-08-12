package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectAttachmentSpec
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInputSource
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInstance
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import cn.coostack.cooparticlesapi.renderer.post.PostEffectType
import net.minecraft.resources.ResourceLocation

/** RenderEntity 的 world attachment 捕获与 fullscreen graph 执行桥。 */
internal object CooPipelineRuntimeEffect {
    private val effectType = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "effect/pipeline"
    )
    private var warnedMissingCaptureBackend = false
    private val capturedBatches = ArrayList<PostEffectInstance>()
    private val capturedOwners = linkedSetOf<String>()
    private var nextBatchId = 0

    /** 清理上一帧未消费的 Iris 预捕获批次。 */
    fun beginFrame() {
        capturedBatches.clear()
        capturedOwners.clear()
        nextBatchId = 0
    }

    /** 在客户端注册共享 Pipeline 的后处理执行器。 */
    fun initOnClient() {
        RenderEffectRegistry.register(effectType) { context, effects ->
            var requests = effects.mapNotNull { effect -> effect.payload as? Request }
            if (context.stage == RenderFrameStage.SCENE_POST && capturedBatches.isNotEmpty()) {
                val frozenBatches = capturedBatches.toList()
                capturedBatches.clear()
                PostEffectFrameExecutor.execute(context, frozenBatches)
                requests = requests.filterNot { request -> request.owner in capturedOwners }
                capturedOwners.clear()
            }

            val requestsByBatch = requests.groupBy { request -> request.batchKey() }
            requestsByBatch.forEach pipelineLoop@{ (batchKey, requests) ->
                // 不同亮度的同一 pipeline 必须拥有独立 FBO；否则后捕获的实体会覆盖先前 batch。
                val batchId = nextBatchId++
                val batchOwner = batchKey.runtimeOwner(batchId)
                val canonical = requests.minBy(Request::owner)
                if (context.stage == RenderFrameStage.SCENE_CAPTURE && !canonical.shaderPackHandled) {
                    return@pipelineLoop
                }
                val attachmentsByFramebuffer = requests
                    .flatMap(Request::attachments)
                    .groupBy(CooCompiledAttachment::framebuffer)
                val batchTargets = attachmentsByFramebuffer.keys.associateWith { framebuffer ->
                    framebuffer.batchTarget(batchId)
                }
                fun attachmentSpecs(attachments: List<CooCompiledAttachment>): List<PostEffectAttachmentSpec> {
                    val formats = attachments.map { it.output.format }.distinct()
                    val mipLevels = attachments.map { it.output.mipLevels }.distinct()
                    require(formats.size == 1 && mipLevels.size == 1) {
                        "A framebuffer must use one color format and mip count for all attachments"
                    }
                    val attachmentCount = attachments.maxOf { it.output.attachment } + 1
                    val spec = PostEffectAttachmentSpec(formats.single(), mipLevels.single())
                    return List(attachmentCount) { spec }
                }
                fun captureOffscreen(): Boolean {
                    return attachmentsByFramebuffer.all { (framebuffer, attachments) ->
                        PostEffectFrameExecutor.captureAttachments(
                            context = context,
                            owner = batchOwner,
                            target = batchTargets.getValue(framebuffer),
                            attachments = attachmentSpecs(attachments)
                        ) {
                            requests.forEach requestLoop@{ request ->
                                request.attachments
                                    .asSequence()
                                    .filter { attachment -> attachment.framebuffer == framebuffer }
                                    .distinctBy { attachment -> attachment.output.node }
                                    .forEach { attachment -> request.render(attachment.output) }
                            }
                        }
                    }
                }
                if (context.stage == RenderFrameStage.SCENE_CAPTURE) {
                    val captured = captureOffscreen()
                    if (captured) {
                        capturedBatches += canonical.postEffect.withBatchUniforms(batchKey, batchOwner, batchTargets)
                        capturedOwners += requests.map(Request::owner)
                    }
                    return@pipelineLoop
                }
                val inlineEntry = attachmentsByFramebuffer.entries.singleOrNull()
                val inlineCaptured = context.stage == RenderFrameStage.SCENE_POST &&
                    (context.backend === VanillaSafeRenderBackend ||
                        requests.all { request -> !request.shaderPackHandled }) &&
                    inlineEntry != null &&
                    requests.all { request -> request.inlineCaptureCompatible } &&
                    PostEffectFrameExecutor.captureInlineAttachments(
                        context = context,
                        owner = batchOwner,
                        target = batchTargets.getValue(inlineEntry.key),
                        attachments = attachmentSpecs(inlineEntry.value)
                    ) {
                        requests.forEach { request -> request.renderWorld() }
                    }
                val captured = if (inlineCaptured) {
                    true
                } else {
                    if (context.stage == RenderFrameStage.SCENE_POST) {
                        requests.forEach { request -> request.renderWorld() }
                    }
                    captureOffscreen()
                }
                if (!captured) {
                    if (!warnedMissingCaptureBackend) {
                        warnedMissingCaptureBackend = true
                        CooParticlesConstants.logger.warn(
                            "Skipping pipeline world attachments because the active post backend cannot capture them"
                        )
                    }
                    return@pipelineLoop
                }
                val postEffect = canonical.postEffect.withBatchUniforms(batchKey, batchOwner, batchTargets)
                PostEffectFrameExecutor.execute(context, listOf(postEffect))
            }
        }
    }

    /** 创建一个带 world attachment 捕获回调的 Pipeline 后处理请求。 */
    fun descriptor(
        owner: String,
        compiled: CooCompiledPipeline,
        postEffect: PostEffectInstance,
        attachments: List<CooCompiledAttachment>,
        shaderPackHandled: Boolean = false,
        renderWorld: () -> Unit,
        render: (CooPipelineOutputPort) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = effectType,
            effectId = "$owner:pipeline",
            sourceInstanceId = owner,
            requiredCapabilities = compiled.requiredCapabilities,
            payload = Request(
                owner,
                postEffect,
                attachments,
                shaderPackHandled,
                supportsInlineCapture(compiled, attachments),
                renderWorld,
                render
            )
        )
    }

    /** 单个实体提交的 Pipeline 请求；多个请求会按 PipelineBatchKey 合并。 */
    private data class Request(
        val owner: String,
        val postEffect: PostEffectInstance,
        val attachments: List<CooCompiledAttachment>,
        val shaderPackHandled: Boolean,
        val inlineCaptureCompatible: Boolean,
        val renderWorld: () -> Unit,
        val render: (CooPipelineOutputPort) -> Unit
    ) {
        /** 解析会影响 fullscreen 结果的参数，作为当前请求的合批键。 */
        fun batchKey(): PipelineBatchKey {
            return PipelineBatchKey(
                pipelineId = postEffect.type.id,
                shaderPackHandled = shaderPackHandled,
                params = postEffect.params,
                uniforms = postEffect.type.chain.passes.map { pass ->
                    PassUniformValues(
                        passName = pass.name,
                        values = pass.uniforms.associate { uniform ->
                            uniform.name to uniform.provider(postEffect)
                        }
                    )
                }
            )
        }
    }

    /** 只允许一个同时写入世界颜色和非零 attachment 的 world 节点进入 inline MRT。 */
    private fun supportsInlineCapture(
        compiled: CooCompiledPipeline,
        attachments: List<CooCompiledAttachment>
    ): Boolean {
        if (attachments.isEmpty() || attachments.any { it.output.attachment == 0 }) return false
        if (attachments.map(CooCompiledAttachment::framebuffer).distinct().size != 1) return false
        val attachmentNodes = attachments.map { it.output.node }.toSet()
        if (attachmentNodes.size != 1) return false
        val worldNodes = compiled.lines.mapNotNull { line ->
            if (line.input !is CooPipelineTarget.World) return@mapNotNull null
            (line.output as? CooPipelineOutputPort)?.node
        }.toSet()
        return worldNodes == attachmentNodes
    }

    /** Pipeline ID、参数快照和 fullscreen uniform 的不可变合批键。 */
    private data class PipelineBatchKey(
        val pipelineId: ResourceLocation,
        val shaderPackHandled: Boolean,
        val params: PostEffectParams,
        val uniforms: List<PassUniformValues>
    )

    /** 一个 fullscreen pass 在当前请求中解析出的全部 uniform。 */
    private data class PassUniformValues(
        val passName: String,
        val values: Map<String, PostEffectParamValue?>
    )

    /** 冻结已参与分组的 uniform，并使用可跨帧复用的实例 ID。 */
    private fun PostEffectInstance.withBatchUniforms(
        key: PipelineBatchKey,
        batchOwner: String,
        batchTargets: Map<ResourceLocation, ResourceLocation>
    ): PostEffectInstance {
        val overrides = key.uniforms.flatMap { pass ->
            pass.values.map { (name, value) -> (pass.passName to name) to value }
        }.toMap()
        return copy(
            type = type.withBatchTargets(batchTargets),
            instanceId = batchOwner,
            uniformOverrides = overrides
        )
    }

    /** 为一个参数 batch 生成稳定且不会互相覆盖的运行时 owner。 */
    private fun PipelineBatchKey.runtimeOwner(batchId: Int): String {
        return "${pipelineId.namespace}/${pipelineId.path}:batch_$batchId"
    }

    /** 为同一 pipeline 的不同 batch 隔离命名 framebuffer。 */
    private fun ResourceLocation.batchTarget(batchId: Int): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(namespace, "$path/batch_$batchId")
    }

    /** 把 world attachment 的资源 id 重写为当前参数 batch 的独立目标。 */
    private fun PostEffectType.withBatchTargets(
        targets: Map<ResourceLocation, ResourceLocation>
    ): PostEffectType {
        if (targets.isEmpty()) return this
        val remappedPasses = chain.passes.map { pass ->
            pass.copy(
                inputs = pass.inputs.map { input ->
                    val source = input.sourceResourceId
                    if (input.source == PostEffectInputSource.SCENE_RESOURCE && source != null) {
                        targets[source]?.let { target -> input.copy(sourceResourceId = target) } ?: input
                    } else {
                        input
                    }
                }
            )
        }
        return PostEffectType(
            id = id,
            model = model,
            chain = chain.copy(passes = remappedPasses),
            requiredCapabilities = requiredCapabilities,
            optionalCapabilities = optionalCapabilities,
            paramUniformNames = paramUniformNames,
            defaultPriority = defaultPriority,
            defaultSubject = defaultSubject,
            descriptorFactory = descriptorFactory
        )
    }
}
