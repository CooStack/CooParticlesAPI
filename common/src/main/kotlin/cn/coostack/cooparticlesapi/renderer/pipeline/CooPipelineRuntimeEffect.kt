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
            effects.forEach effectLoop@{ effect ->
                val request = effect.payload as? Request ?: return@effectLoop
                val captured = request.attachments.groupBy(CooCompiledAttachment::framebuffer).all { (framebuffer, attachments) ->
                    PostEffectFrameExecutor.captureAttachments(
                        context = context,
                        owner = request.owner,
                        target = framebuffer,
                        attachmentCount = attachments.maxOf { it.output.attachment } + 1
                    ) {
                        request.render(attachments.first().output)
                    }
                }
                if (!captured) {
                    if (!warnedMissingCaptureBackend) {
                        warnedMissingCaptureBackend = true
                        CooParticlesConstants.logger.warn(
                            "Skipping pipeline world attachments because the active post backend cannot capture them"
                        )
                    }
                    return@effectLoop
                }
                PostEffectFrameExecutor.execute(context, listOf(request.postEffect))
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
