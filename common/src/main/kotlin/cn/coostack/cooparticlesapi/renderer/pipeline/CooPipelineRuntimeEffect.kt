package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectExecutor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInstance
import net.minecraft.resources.ResourceLocation

/** RenderEntity 的 world attachment 捕获与 fullscreen graph 执行桥。 */
internal object CooPipelineRuntimeEffect {
    private val effectType = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "effect/pipeline"
    )
    private var warnedMissingCaptureBackend = false

    fun initOnClient() {
        RenderEffectRegistry.register(effectType, RenderEffectExecutor { context, effects ->
            val requestsByPipeline = effects
                .mapNotNull { effect -> effect.payload as? Request }
                .groupBy { request -> request.postEffect.type.id }
            requestsByPipeline.forEach pipelineLoop@{ (pipelineId, requests) ->
                val attachmentsByFramebuffer = requests
                    .flatMap(Request::attachments)
                    .groupBy(CooCompiledAttachment::framebuffer)
                val captured = attachmentsByFramebuffer.all { (framebuffer, attachments) ->
                    PostEffectFrameExecutor.captureAttachments(
                        context = context,
                        owner = pipelineId.toString(),
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
                val postEffect = requests.minBy(Request::owner).postEffect
                PostEffectFrameExecutor.execute(context, listOf(postEffect))
            }
        })
    }

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

    private data class Request(
        val owner: String,
        val postEffect: PostEffectInstance,
        val attachments: List<CooCompiledAttachment>,
        val render: (CooPipelineOutputPort) -> Unit
    )
}
