package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostEffectCapturedAttachmentTest {
    @Test
    fun `captured named attachment satisfies required scene resource input`() {
        val target = ResourceLocation.fromNamespaceAndPath("test", "pipeline/world")
        val backend = CapturingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )
        val type = PostEffectType(
            id = ResourceLocation.fromNamespaceAndPath("test", "pipeline_post"),
            model = PostEffectModel.SCREEN_QUAD,
            chain = PostEffectChain(
                listOf(
                    PostEffectPass(
                        name = "composite",
                        fragment = ResourceLocation.fromNamespaceAndPath("test", "composite.fsh"),
                        inputs = listOf(
                            PostEffectInput(
                                samplerName = "Mask",
                                source = PostEffectInputSource.SCENE_RESOURCE,
                                sourceResourceId = target,
                                sourceResourceAttachment = 1
                            )
                        ),
                        output = PostEffectOutput.FINAL_SCREEN
                    )
                )
            ),
            requiredCapabilities = emptySet(),
            optionalCapabilities = emptySet()
        )

        PostEffectFrameExecutor.installBackend(backend)
        try {
            var renderCount = 0
            assertTrue(
                PostEffectFrameExecutor.captureAttachments(context, "owner", target, 2) {
                    renderCount++
                }
            )
            assertEquals(1, renderCount)
            val plan = PostEffectFrameExecutor.buildPlan(context, type.create(instanceId = "owner"))
            assertTrue(plan.steps.single().executable)
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    private class CapturingBackend : PostEffectExecutionBackend, PostEffectAttachmentPreparationBackend {
        private val attachments = mutableSetOf<Pair<ResourceLocation, Int>>()

        override fun execute(step: PostEffectExecutionStep) = Unit

        override fun captureAttachment(
            context: RenderFrameContext,
            owner: String,
            target: ResourceLocation,
            attachment: Int,
            render: () -> Unit
        ): Boolean {
            render()
            attachments += target to attachment
            return true
        }

        override fun captureAttachments(
            context: RenderFrameContext,
            owner: String,
            target: ResourceLocation,
            attachmentCount: Int,
            render: () -> Unit
        ): Boolean {
            render()
            repeat(attachmentCount) { attachment ->
                attachments += target to attachment
            }
            return true
        }

        override fun hasAttachment(target: ResourceLocation, attachment: Int): Boolean {
            return target to attachment in attachments
        }
    }
}
