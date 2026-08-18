package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.post.PostEffectInput
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class CooPipelineNodeChannelLineTest {
    @Test
    fun `node channel line connects multiple outputs to multiple sampler slots`() {
        val pipeline = CooPipelines.generic<Any>(id("multi_channel_line")) {
            val gbuffer = pass("gbuffer") {
                fragment(id("gbuffer.fsh"))
                colorAttachments(2)
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("Albedo", textureSlot = 0)
                input("Normal", textureSlot = 1)
            }

            line(gbuffer, 0, composite, 0)
            line(gbuffer, 1, composite, 1)
            line(composite.color(), screenTarget())
        }

        val postEffect = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
        val inputsBySlot = postEffect.type.chain.passes
            .single { pass -> pass.name == "composite" }
            .inputs
            .associateBy(PostEffectInput::textureSlot)

        assertEquals("gbuffer", inputsBySlot.getValue(0).sourcePassName)
        assertEquals(0, inputsBySlot.getValue(0).sourcePassAttachment)
        assertEquals("gbuffer", inputsBySlot.getValue(1).sourcePassName)
        assertEquals(1, inputsBySlot.getValue(1).sourcePassAttachment)
    }

    @Test
    fun `node channel line connects mask output by attachment location`() {
        val pipeline = CooPipelines.generic<Any>(id("mask_channel_line")) {
            val source = world("source") {
                vertex(id("source.vsh"))
                fragment(id("source.fsh"))
                maskOutput()
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("Mask", textureSlot = 0)
            }

            line(source, 1, composite, 0)
            line(composite.color(), screenTarget())
        }

        val input = requireNotNull(CooPipelinePostEffectCompiler.compile(pipeline))
            .type.chain.passes.single { pass -> pass.name == "composite" }
            .inputs.single()

        assertEquals(1, input.sourceResourceAttachment)
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("test", path)
    }
}
