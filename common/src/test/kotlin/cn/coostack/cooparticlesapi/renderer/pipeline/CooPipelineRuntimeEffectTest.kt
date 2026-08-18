package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.IrisSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.backend.VanillaSafeRenderBackend
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.renderer.post.PostEffectAttachmentPreparationBackend
import cn.coostack.cooparticlesapi.renderer.post.PostEffectAttachmentSpec
import cn.coostack.cooparticlesapi.renderer.post.PostEffectExecutionBackend
import cn.coostack.cooparticlesapi.renderer.post.PostEffectExecutionStep
import cn.coostack.cooparticlesapi.renderer.post.PostEffectFrameExecutor
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityPipelineRuntime
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertEquals

class CooPipelineRuntimeEffectTest {
    private data class BloomSubject(var intensity: Float)
    private data class LaserBloomSubject(val brightness: Float)

    @Test
    fun `iris scene capture reuses world attachments after final pass`() {
        val geometryRenders = mutableMapOf("entity" to 0)
        val descriptor = descriptors(
            pipeline(id("iris_split")),
            "entity",
            1,
            geometryRenders,
            shaderPackHandled = true
        ).single()
        val backend = CountingBackend()
        val captureContext = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend,
            stage = RenderFrameStage.SCENE_CAPTURE
        )
        val compositeContext = captureContext.copy(stage = RenderFrameStage.SCENE_POST)

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptor.effectType))
            executor.render(captureContext, listOf(descriptor))
            executor.render(compositeContext, listOf(descriptor))

            assertEquals(1, backend.captureCount)
            assertEquals(1, backend.executedPassCount)
            assertEquals(1, geometryRenders.getValue("entity"))
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `same pipeline id captures all entity geometry and executes fullscreen graph once`() {
        val sharedPipelineId = id("shared")
        val pipelineA = pipeline(sharedPipelineId)
        val pipelineB = pipeline(sharedPipelineId)
        val pipelineCId = id("pipeline_c")
        val pipelineDId = id("pipeline_d")
        val pipelineC = pipeline(pipelineCId)
        val pipelineD = pipeline(pipelineDId)
        val geometryRenders = linkedMapOf(
            "a" to 0,
            "b" to 0,
            "c" to 0,
            "d" to 0
        )
        val descriptors = buildList {
            addAll(descriptors(pipelineA, "a", 10, geometryRenders))
            addAll(descriptors(pipelineB, "b", 5, geometryRenders))
            addAll(descriptors(pipelineC, "c", 6, geometryRenders))
            addAll(descriptors(pipelineD, "d", 7, geometryRenders))
        }
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )

        CooPipelineRuntimeEffect.initOnClient()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(context, descriptors)

            assertEquals(mapOf("a" to 10, "b" to 5, "c" to 6, "d" to 7), geometryRenders)
            assertEquals(3, backend.captureCount)
            assertEquals(3, backend.executedPassCount)
            assertEquals(3, backend.captureOwners.toSet().size)
            assertEquals(
                setOf(
                    "${sharedPipelineId.namespace}/${sharedPipelineId.path}",
                    "${pipelineCId.namespace}/${pipelineCId.path}",
                    "${pipelineDId.namespace}/${pipelineDId.path}"
                ),
                backend.captureOwners.map { owner -> owner.substringBefore(":batch_") }.toSet()
            )
            assertEquals(
                mapOf(sharedPipelineId to 1, pipelineCId to 1, pipelineDId to 1),
                backend.executedPipelineIds.groupingBy { pipelineId -> pipelineId }.eachCount()
            )
            assertEquals(
                backend.captureOwners.toSet(),
                backend.executedInstanceIds.toSet()
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `twelve equal mask bloom entities execute one three pass fullscreen graph`() {
        var providerResolveCount = 0
        var geometryRenderCount = 0
        val pipeline = CooPipelines.MASK_BLOOM.intensity { subject: LaserBloomSubject ->
            providerResolveCount++
            subject.brightness * 19.2F
        }
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val descriptors = List(12) { index ->
            val subject = LaserBloomSubject(5F)
            CooPipelineRuntimeEffect.descriptor(
                owner = "laser-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(
                    instanceId = "laser-$index:pipeline",
                    subject = subject
                ),
                attachments = runtime.worldAttachments,
                shaderPackHandled = true,
                renderWorld = {}
            ) {
                geometryRenderCount++
            }
        }
        val backend = CountingBackend()
        val captureContext = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend,
            stage = RenderFrameStage.SCENE_CAPTURE
        )
        val postContext = captureContext.copy(
            stage = RenderFrameStage.SCENE_POST,
            sceneColorTextureId = 1
        )

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(captureContext, descriptors)
            executor.render(postContext, descriptors)

            assertEquals(1, backend.captureCount)
            assertEquals(12, geometryRenderCount)
            assertEquals(3, backend.executedPassCount)
            assertEquals(12, providerResolveCount)
            assertEquals(
                listOf(List(2) { PostEffectAttachmentSpec(CooTextureFormat.RGBA16F, 1) }),
                backend.capturedSpecs
            )
            assertEquals(
                listOf(PostEffectParamValue.FloatValue(96F)),
                backend.executedUniforms.mapNotNull { uniforms -> uniforms["Intensity"] }
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `twelve equal mask bloom entities use one vanilla inline capture without offscreen replay`() {
        var worldRenderCount = 0
        var offscreenRenderCount = 0
        val pipeline = CooPipelines.MASK_BLOOM.intensity { subject: LaserBloomSubject ->
            subject.brightness * 19.2F
        }
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val descriptors = List(12) { index ->
            val subject = LaserBloomSubject(5F)
            CooPipelineRuntimeEffect.descriptor(
                owner = "vanilla-laser-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(
                    instanceId = "vanilla-laser-$index:pipeline",
                    subject = subject
                ),
                attachments = runtime.worldAttachments,
                renderWorld = { worldRenderCount++ }
            ) {
                offscreenRenderCount++
            }
        }
        val backend = CountingBackend(inlineCaptureSupported = true)
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend,
            stage = RenderFrameStage.SCENE_POST,
            sceneColorTextureId = 1
        )

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(context, descriptors)

            assertEquals(1, backend.inlineCaptureCount)
            assertEquals(0, backend.captureCount)
            assertEquals(12, worldRenderCount)
            assertEquals(0, offscreenRenderCount)
            assertEquals(3, backend.executedPassCount)
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `vanilla inline capture failure renders world once and replays offscreen once`() {
        var worldRenderCount = 0
        var offscreenRenderCount = 0
        val runtime = RenderEntityPipelineRuntime(CooPipelines.MASK_BLOOM)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val descriptors = List(12) { index ->
            CooPipelineRuntimeEffect.descriptor(
                owner = "fallback-laser-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(instanceId = "fallback-laser-$index:pipeline"),
                attachments = runtime.worldAttachments,
                renderWorld = { worldRenderCount++ }
            ) {
                offscreenRenderCount++
            }
        }
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend,
            stage = RenderFrameStage.SCENE_POST,
            sceneColorTextureId = 1
        )

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(context, descriptors)

            assertEquals(1, backend.inlineCaptureCount)
            assertEquals(1, backend.captureCount)
            assertEquals(12, worldRenderCount)
            assertEquals(12, offscreenRenderCount)
            assertEquals(3, backend.executedPassCount)
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `iris bypass skips pre final capture and renders once after final pass`() {
        var worldRenderCount = 0
        var offscreenRenderCount = 0
        val runtime = RenderEntityPipelineRuntime(CooPipelines.MASK_BLOOM)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val descriptor = CooPipelineRuntimeEffect.descriptor(
            owner = "iris-bypass",
            compiled = runtime.compiledPipeline,
            postEffect = postEffect.type.create(instanceId = "iris-bypass:pipeline"),
            attachments = runtime.worldAttachments,
            shaderPackHandled = false,
            renderWorld = { worldRenderCount++ }
        ) {
            offscreenRenderCount++
        }
        val backend = CountingBackend(inlineCaptureSupported = true)
        val captureContext = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = IrisSafeRenderBackend,
            stage = RenderFrameStage.SCENE_CAPTURE
        )
        val postContext = captureContext.copy(
            stage = RenderFrameStage.SCENE_POST,
            sceneColorTextureId = 1
        )

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptor.effectType))
            executor.render(captureContext, listOf(descriptor))
            executor.render(postContext, listOf(descriptor))

            assertEquals(1, backend.inlineCaptureCount)
            assertEquals(0, backend.captureCount)
            assertEquals(1, worldRenderCount)
            assertEquals(0, offscreenRenderCount)
            assertEquals(3, backend.executedPassCount)
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `iris handled and bypass renderers keep separate batches`() {
        var handledOffscreenRenders = 0
        var bypassWorldRenders = 0
        var bypassOffscreenRenders = 0
        val runtime = RenderEntityPipelineRuntime(CooPipelines.MASK_BLOOM)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val handled = CooPipelineRuntimeEffect.descriptor(
            owner = "iris-handled",
            compiled = runtime.compiledPipeline,
            postEffect = postEffect.type.create(instanceId = "iris-handled:pipeline"),
            attachments = runtime.worldAttachments,
            shaderPackHandled = true,
            renderWorld = {}
        ) {
            handledOffscreenRenders++
        }
        val bypass = CooPipelineRuntimeEffect.descriptor(
            owner = "iris-bypass",
            compiled = runtime.compiledPipeline,
            postEffect = postEffect.type.create(instanceId = "iris-bypass:pipeline"),
            attachments = runtime.worldAttachments,
            shaderPackHandled = false,
            renderWorld = { bypassWorldRenders++ }
        ) {
            bypassOffscreenRenders++
        }
        val descriptors = listOf(handled, bypass)
        val backend = CountingBackend(inlineCaptureSupported = true)
        val captureContext = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = IrisSafeRenderBackend,
            stage = RenderFrameStage.SCENE_CAPTURE
        )
        val postContext = captureContext.copy(
            stage = RenderFrameStage.SCENE_POST,
            sceneColorTextureId = 1
        )

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(handled.effectType))
            executor.render(captureContext, descriptors)
            executor.render(postContext, descriptors)

            assertEquals(1, backend.captureCount)
            assertEquals(1, backend.inlineCaptureCount)
            assertEquals(1, handledOffscreenRenders)
            assertEquals(1, bypassWorldRenders)
            assertEquals(0, bypassOffscreenRenders)
            assertEquals(6, backend.executedPassCount)
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `pipeline batch keeps the same runtime owner across frames`() {
        val pipelineId = id("stable_owner")
        val geometryRenders = mutableMapOf("entity" to 0)
        val descriptor = descriptors(pipeline(pipelineId), "entity", 1, geometryRenders).single()
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )

        CooPipelineRuntimeEffect.initOnClient()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptor.effectType))
            CooPipelineRuntimeEffect.beginFrame()
            executor.render(context, listOf(descriptor))
            CooPipelineRuntimeEffect.beginFrame()
            executor.render(context, listOf(descriptor))

            assertEquals(backend.captureOwners[0], backend.captureOwners[1])
            assertEquals(backend.captureOwners, backend.executedInstanceIds)
            assertEquals("test/stable_owner", backend.captureOwners[0].substringBefore(":batch_"))
            assertEquals(2, geometryRenders.getValue("entity"))
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `iris scene capture freezes different brightness batches until scene post`() {
        var providerResolveCount = 0
        val pipeline = reusableFullscreenParameterPipeline().parameter("strength") { subject: BloomSubject ->
            providerResolveCount++
            subject.intensity
        }
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val subjects = listOf(BloomSubject(1F), BloomSubject(8F))
        val descriptors = subjects.mapIndexed { index, subject ->
            CooPipelineRuntimeEffect.descriptor(
                owner = "iris-entity-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(
                    instanceId = "iris-entity-$index:pipeline",
                    subject = subject
                ),
                attachments = runtime.worldAttachments,
                shaderPackHandled = true,
                renderWorld = {}
            ) {
                subject.intensity = 99F
            }
        }
        val backend = CountingBackend()
        val captureContext = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend,
            stage = RenderFrameStage.SCENE_CAPTURE
        )
        val postContext = captureContext.copy(stage = RenderFrameStage.SCENE_POST)

        CooPipelineRuntimeEffect.initOnClient()
        CooPipelineRuntimeEffect.beginFrame()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(captureContext, descriptors)
            executor.render(postContext, descriptors)

            assertEquals(2, backend.captureCount)
            assertEquals(2, backend.captureTargets.toSet().size)
            assertEquals(2, backend.executedPassCount)
            assertEquals(backend.captureOwners.toSet(), backend.executedInstanceIds.toSet())
            assertEquals(
                backend.captureTargets.toSet(),
                backend.executedInputResourceIds.toSet()
            )
            assertEquals(2, providerResolveCount)
            assertEquals(listOf(99F, 99F), subjects.map(BloomSubject::intensity))
            assertEquals(
                setOf(PostEffectParamValue.FloatValue(1F), PostEffectParamValue.FloatValue(8F)),
                backend.executedUniforms.mapNotNull { uniforms -> uniforms["PassStrength"] }.toSet()
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `shared framebuffer renders every contributing world node once`() {
        val pipeline = sharedFramebufferPipeline()
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val renderedNodes = mutableListOf<String>()
        val descriptor = CooPipelineRuntimeEffect.descriptor(
            owner = "entity",
            compiled = runtime.compiledPipeline,
            postEffect = postEffect.type.create(instanceId = "entity:pipeline"),
            attachments = runtime.worldAttachments,
            renderWorld = {}
        ) { output ->
            renderedNodes += output.node
        }
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )

        CooPipelineRuntimeEffect.initOnClient()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptor.effectType))
            executor.render(context, listOf(descriptor))

            assertEquals(listOf("first", "second"), renderedNodes.sorted())
            assertEquals(1, backend.captureCount)
            assertEquals(1, backend.executedPassCount)
            assertEquals(
                List(2) { PostEffectAttachmentSpec(CooTextureFormat.RGBA16F, 3) },
                backend.capturedSpecs.single()
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `reusable pipeline binds different entity parameters before one fullscreen graph`() {
        val pipeline = reusableWorldParameterPipeline().parameter("strength") { subject: BloomSubject ->
            subject.intensity
        }
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val resolvedStrengths = mutableListOf<CooUniformValue?>()
        val descriptors = listOf(BloomSubject(1F), BloomSubject(2F)).mapIndexed { index, subject ->
            CooPipelineRuntimeEffect.descriptor(
                owner = "entity-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(
                    instanceId = "entity-$index:pipeline",
                    subject = subject
                ),
                attachments = runtime.worldAttachments,
                renderWorld = {}
            ) { output ->
                resolvedStrengths += pipeline.resolveUniform(output.node, "EntityStrength", subject)
            }
        }
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )

        CooPipelineRuntimeEffect.initOnClient()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(context, descriptors)

            assertEquals(
                listOf<CooUniformValue?>(CooUniformValue.FloatValue(1F), CooUniformValue.FloatValue(2F)),
                resolvedStrengths
            )
            assertEquals(1, backend.captureCount)
            assertEquals(1, backend.executedPassCount)
            assertEquals(
                listOf(PostEffectParamValue.FloatValue(4F)),
                backend.executedUniforms.mapNotNull { uniforms -> uniforms["PassStrength"] }
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    @Test
    fun `reusable pipeline splits different fullscreen parameter values`() {
        var providerResolveCount = 0
        val pipeline = reusableFullscreenParameterPipeline().parameter("strength") { subject: BloomSubject ->
            providerResolveCount++
            subject.intensity
        }
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        val subjects = listOf(BloomSubject(1F), BloomSubject(2F))
        val descriptors = subjects.mapIndexed { index, subject ->
            CooPipelineRuntimeEffect.descriptor(
                owner = "entity-$index",
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(
                    instanceId = "entity-$index:pipeline",
                    subject = subject
                ),
                attachments = runtime.worldAttachments,
                renderWorld = {}
            ) {
                subject.intensity = 99F
            }
        }
        val backend = CountingBackend()
        val context = RenderFrameContext(
            tickDelta = 0F,
            viewMatrix = Matrix4f(),
            projMatrix = Matrix4f(),
            backend = VanillaSafeRenderBackend
        )

        CooPipelineRuntimeEffect.initOnClient()
        PostEffectFrameExecutor.installBackend(backend)
        try {
            val executor = requireNotNull(RenderEffectRegistry.get(descriptors.first().effectType))
            executor.render(context, descriptors)

            assertEquals(2, backend.captureCount)
            assertEquals(2, backend.executedPassCount)
            assertEquals(2, backend.captureTargets.toSet().size)
            assertEquals(2, backend.executedInstanceIds.toSet().size)
            assertEquals(2, providerResolveCount)
            assertEquals(listOf(99F, 99F), subjects.map(BloomSubject::intensity))
            assertEquals(
                setOf(PostEffectParamValue.FloatValue(1F), PostEffectParamValue.FloatValue(2F)),
                backend.executedUniforms.mapNotNull { uniforms -> uniforms["PassStrength"] }.toSet()
            )
        } finally {
            PostEffectFrameExecutor.resetBackend()
        }
    }

    private fun descriptors(
        pipeline: CooRenderPipeline<Any>,
        ownerPrefix: String,
        count: Int,
        geometryRenders: MutableMap<String, Int>,
        shaderPackHandled: Boolean = false
    ): List<RenderEffectDescriptor> {
        val runtime = RenderEntityPipelineRuntime(pipeline)
        val postEffect = requireNotNull(runtime.compiledPostEffect)
        return List(count) { index ->
            val owner = "$ownerPrefix-$index"
            CooPipelineRuntimeEffect.descriptor(
                owner = owner,
                compiled = runtime.compiledPipeline,
                postEffect = postEffect.type.create(instanceId = "$owner:pipeline"),
                attachments = runtime.worldAttachments,
                shaderPackHandled = shaderPackHandled,
                renderWorld = {}
            ) {
                geometryRenders[ownerPrefix] = geometryRenders.getValue(ownerPrefix) + 1
            }
        }
    }

    private fun pipeline(id: ResourceLocation): CooRenderPipeline<Any> {
        return CooPipelines.entity(id) {
            val geometry = world("geometry") {
                vertex(id("geometry.vsh"))
                fragment(id("geometry.fsh"))
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("Input")
            }

            line(geometry.color(), composite.input("Input"))
            line(composite.color(), screenTarget())
        }
    }

    private fun sharedFramebufferPipeline(): CooRenderPipeline<Any> {
        val framebuffer = id("framebuffer/shared_world")
        return CooPipelines.entity(id("shared_world_nodes")) {
            val first = world("first") {
                vertex(id("geometry.vsh"))
                fragment(id("first.fsh"))
                outputFormat(CooTextureFormat.RGBA16F)
                mipLevels(3)
            }
            val second = world("second") {
                vertex(id("geometry.vsh"))
                fragment(id("second.fsh"))
                colorAttachments(2)
                outputFormat(CooTextureFormat.RGBA16F)
                mipLevels(3)
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("First", textureSlot = 0)
                input("Second", textureSlot = 1)
            }

            line(first.color(), framebufferTarget(framebuffer))
            line(second.color(1), framebufferTarget(framebuffer, 1))
            line(framebuffer(framebuffer), composite.input("First"))
            line(framebuffer(framebuffer, 1), composite.input("Second"))
            line(composite.color(), screenTarget())
        }
    }

    private fun reusableWorldParameterPipeline(): CooRenderPipeline<Nothing> {
        return CooPipelines.entity<Nothing>(id("reusable_world_parameter")) {
            val geometry = world("geometry") {
                vertex(id("geometry.vsh"))
                fragment(id("geometry.fsh"))
                uniform("EntityStrength", 1F)
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("Input")
                uniform("PassStrength", 4F)
            }

            line(geometry.color(), composite.input("Input"))
            line(composite.color(), screenTarget())
            parameter("strength", geometry, "EntityStrength")
        }
    }

    private fun reusableFullscreenParameterPipeline(): CooRenderPipeline<Nothing> {
        return CooPipelines.entity<Nothing>(id("reusable_fullscreen_parameter")) {
            val geometry = world("geometry") {
                vertex(id("geometry.vsh"))
                fragment(id("geometry.fsh"))
            }
            val composite = pass("composite") {
                fragment(id("composite.fsh"))
                input("Input")
                uniform("PassStrength", 1F)
            }

            line(geometry.color(), composite.input("Input"))
            line(composite.color(), screenTarget())
            parameter("strength", composite, "PassStrength")
        }
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("test", path)
    }

    private class CountingBackend(
        private val inlineCaptureSupported: Boolean = false
    ) : PostEffectExecutionBackend, PostEffectAttachmentPreparationBackend {
        private val attachments = mutableSetOf<Pair<ResourceLocation, Int>>()
        val captureOwners = mutableListOf<String>()
        val captureTargets = mutableListOf<ResourceLocation>()
        val executedPipelineIds = mutableListOf<ResourceLocation>()
        val executedInstanceIds = mutableListOf<String>()
        val executedInputResourceIds = mutableListOf<ResourceLocation>()
        val executedUniforms = mutableListOf<Map<String, PostEffectParamValue>>()
        val capturedSpecs = mutableListOf<List<PostEffectAttachmentSpec>>()
        var captureCount = 0
            private set
        var inlineCaptureCount = 0
            private set
        var executedPassCount = 0
            private set

        override fun captureInlineAttachments(
            context: RenderFrameContext,
            owner: String,
            target: ResourceLocation,
            attachments: List<PostEffectAttachmentSpec>,
            render: () -> Unit
        ): Boolean {
            inlineCaptureCount++
            if (!inlineCaptureSupported) return false
            captureOwners += owner
            captureTargets += target
            capturedSpecs += attachments
            render()
            attachments.indices.forEach { attachment ->
                this.attachments += target to attachment
            }
            return true
        }

        override fun execute(step: PostEffectExecutionStep) {
            executedPassCount++
            executedPipelineIds += step.instance.type.id
            executedInstanceIds += step.instance.instanceId
            executedInputResourceIds += step.inputs.mapNotNull { input -> input.sourceResourceId }
            executedUniforms += step.uniforms
        }

        override fun captureAttachment(
            context: RenderFrameContext,
            owner: String,
            target: ResourceLocation,
            attachment: Int,
            render: () -> Unit
        ): Boolean {
            captureCount++
            captureOwners += owner
            captureTargets += target
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
            captureCount++
            captureOwners += owner
            captureTargets += target
            render()
            repeat(attachmentCount) { attachment ->
                attachments += target to attachment
            }
            return true
        }

        override fun captureAttachments(
            context: RenderFrameContext,
            owner: String,
            target: ResourceLocation,
            attachments: List<PostEffectAttachmentSpec>,
            render: () -> Unit
        ): Boolean {
            captureCount++
            captureOwners += owner
            captureTargets += target
            capturedSpecs += attachments
            render()
            attachments.indices.forEach { attachment ->
                this.attachments += target to attachment
            }
            return true
        }

        override fun hasAttachment(target: ResourceLocation, attachment: Int): Boolean {
            return target to attachment in attachments
        }
    }
}
