package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInstance
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import net.minecraft.resources.ResourceLocation

/** RenderEntity 的 world attachment 捕获与 fullscreen graph 执行桥。 */
internal object CooPipelineRuntimeEffect {
    private val effectType = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "effect/pipeline"
    )
    private var warnedMissingCaptureBackend = false

    /** 在客户端注册共享 Pipeline 的后处理执行器。 */
    fun initOnClient() {
        RenderEffectRegistry.register(effectType) { context, effects ->
            val requestsByBatch = effects
                .mapNotNull { effect -> effect.payload as? Request }
                .groupBy { request -> request.batchKey() }
            requestsByBatch.forEach pipelineLoop@{ (batchKey, requests) ->
                val batchOwner = batchKey.pipelineId.runtimeOwner()
                val canonical = requests.minBy(Request::owner)
                val attachmentsByFramebuffer = requests
                    .flatMap(Request::attachments)
                    .groupBy(CooCompiledAttachment::framebuffer)
                val captured = attachmentsByFramebuffer.all { (framebuffer, attachments) ->
                    PostEffectFrameExecutor.captureAttachments(
                        context = context,
                        owner = batchOwner,
                        target = framebuffer,
                        attachmentCount = attachments.maxOf { it.output.attachment } + 1
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
                if (!captured) {
                    if (!warnedMissingCaptureBackend) {
                        warnedMissingCaptureBackend = true
                        CooParticlesConstants.logger.warn(
                            "Skipping pipeline world attachments because the active post backend cannot capture them"
                        )
                    }
                    return@pipelineLoop
                }
                val postEffect = canonical.postEffect.withBatchUniforms(batchKey, batchOwner)
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
        render: (CooPipelineOutputPort) -> Unit
    ): RenderEffectDescriptor {
        return RenderEffectDescriptor(
            effectType = effectType,
            effectId = "$owner:pipeline",
            sourceInstanceId = owner,
            requiredCapabilities = compiled.requiredCapabilities,
            payload = Request(owner, postEffect, attachments, render)
        )
    }

    /** 单个实体提交的 Pipeline 请求；多个请求会按 PipelineBatchKey 合并。 */
    private data class Request(
        val owner: String,
        val postEffect: PostEffectInstance,
        val attachments: List<CooCompiledAttachment>,
        val render: (CooPipelineOutputPort) -> Unit
    ) {
        /** 解析会影响 fullscreen 结果的参数，作为当前请求的合批键。 */
        fun batchKey(): PipelineBatchKey {
            return PipelineBatchKey(
                pipelineId = postEffect.type.id,
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

    /** Pipeline ID、参数快照和 fullscreen uniform 的不可变合批键。 */
    private data class PipelineBatchKey(
        val pipelineId: ResourceLocation,
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
        batchOwner: String
    ): PostEffectInstance {
        val overrides = key.uniforms.flatMap { pass ->
            pass.values.map { (name, value) -> (pass.passName to name) to value }
        }.toMap()
        return copy(
            instanceId = batchOwner,
            uniformOverrides = overrides
        )
    }

    /** 返回不含后端 target 分隔符的稳定运行时 owner。 */
    private fun ResourceLocation.runtimeOwner(): String = "$namespace/$path"
}
