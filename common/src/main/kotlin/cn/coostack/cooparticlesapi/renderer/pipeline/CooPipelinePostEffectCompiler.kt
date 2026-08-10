package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.post.PostEffectChain
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInput
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInputSource
import cn.coostack.cooparticlesapi.renderer.post.PostEffectModel
import cn.coostack.cooparticlesapi.renderer.post.PostEffectOutput
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParams
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamsBuilder
import cn.coostack.cooparticlesapi.renderer.post.PostEffectPass
import cn.coostack.cooparticlesapi.renderer.post.PostEffectResourceChannel
import cn.coostack.cooparticlesapi.renderer.post.PostEffectType
import cn.coostack.cooparticlesapi.renderer.post.PostEffectUniform
import net.minecraft.resources.ResourceLocation

internal data class CooCompiledPostEffect(
    val type: PostEffectType,
    val defaultParams: PostEffectParams
)

/** 把统一 pipeline 的 fullscreen 子图翻译给现有 post 执行后端。 */
internal object CooPipelinePostEffectCompiler {
    /**
     * 编译 pipeline 的静态图和当前 subject 对应的后处理定义。
     *
     * @param pipeline 待转换的渲染 pipeline
     * @param subject uniform provider 读取的当前对象
     * @return 后处理定义；pipeline 没有全屏节点时返回 `null`
     */
    fun compile(pipeline: CooRenderPipeline<*>, subject: Any = Unit): CooCompiledPostEffect? {
        return compile(pipeline, CooPipelineCompiler.compile(pipeline), subject)
    }

    /**
     * 使用已有静态图编译结果生成当前 subject 的后处理定义。
     *
     * @param pipeline 待转换的渲染 pipeline
     * @param compiled 已完成的静态 pipeline 图编译结果
     * @param subject uniform provider 读取的当前对象
     * @return 后处理定义；pipeline 没有全屏节点时返回 `null`
     */
    fun compile(
        pipeline: CooRenderPipeline<*>,
        compiled: CooCompiledPipeline,
        subject: Any = Unit
    ): CooCompiledPostEffect? {
        val fullscreenNodes = compiled.nodes.filter { it.kind != CooPipelineNodeKind.WORLD }
        if (fullscreenNodes.isEmpty()) return null

        val nodesByName = compiled.nodes.associateBy(CooPipelineNode::name)
        val defaults = PostEffectParamsBuilder()
        val outputPasses = LinkedHashMap<String, String>()
        val passes = buildList {
            fullscreenNodes.forEach { node ->
            val shader = node.shader as CooPipelineShader.Stages
            val inputLines = compiled.lines.filter { line ->
                (line.input as? CooPipelineInputPort)?.node == node.name
            }
            val targets = compiled.lines.filter { line ->
                val output = line.output as? CooPipelineOutputPort
                output?.node == node.name && line.input is CooPipelineTarget
            }
            val finalOutput = targets.map { it.input as CooPipelineTarget }
                .firstOrNull { it is CooPipelineTarget.FinalScreen }
                ?.toPostOutput()
                ?: targets.firstOrNull()?.let { (it.input as CooPipelineTarget).toPostOutput() }
                ?: PostEffectOutput.TEMPORARY
            val framebuffer = compiled.attachments.firstOrNull { it.output.node == node.name }?.framebuffer
            val pingPong = node.pingPong
            if (pingPong == null) {
                add(
                    node.toPostPass(
                        name = node.name,
                        shader = shader,
                        inputs = inputLines.map { line ->
                            line.toPostInput(compiled, nodesByName, outputPasses, defaults)
                        },
                        output = finalOutput,
                        framebuffer = framebuffer
                    )
                )
                outputPasses[node.name] = node.name
                return@forEach
            }

            var previousPass: String? = null
            repeat(pingPong.iterations) { iterationIndex ->
                val passName = "${node.name}_iteration_$iterationIndex"
                val iteration = CooPipelineIteration(iterationIndex, pingPong.iterations)
                val inputs = inputLines.map { line ->
                    val input = line.input as CooPipelineInputPort
                    if (input.sampler == pingPong.feedbackSampler && previousPass != null) {
                        PostEffectInput(
                            samplerName = input.sampler,
                            source = PostEffectInputSource.PASS_OUTPUT,
                            optional = input.optional,
                            sourcePassName = previousPass,
                            sourcePassAttachment = 0,
                            textureSlot = input.textureSlot
                        )
                    } else {
                        line.toPostInput(compiled, nodesByName, outputPasses, defaults)
                    }
                }
                val isLast = iterationIndex == pingPong.iterations - 1
                val pingPongFramebuffer = requireNotNull(framebuffer) {
                    "Ping-pong node '${node.name}' has no compiled framebuffer"
                }
                val targetKey = "$pingPongFramebuffer/${if (iteration.isPing) "ping" else "pong"}"
                add(
                    node.toPostPass(
                        name = passName,
                        shader = shader,
                        inputs = inputs,
                        output = if (isLast) finalOutput else PostEffectOutput.TEMPORARY,
                        framebuffer = framebuffer,
                        iteration = iteration,
                        outputTargetKey = targetKey,
                        reuseOutputTarget = true
                    )
                )
                previousPass = passName
            }
            outputPasses[node.name] = requireNotNull(previousPass)
        }
        }
        return CooCompiledPostEffect(
            type = PostEffectType(
                id = pipeline.id,
                model = PostEffectModel.SCREEN_QUAD,
                chain = PostEffectChain(passes),
                requiredCapabilities = compiled.requiredCapabilities,
                optionalCapabilities = compiled.optionalCapabilities,
                defaultSubject = subject
            ),
            defaultParams = defaults.build()
        )
    }

    private fun CooPipelineLine.toPostInput(
        compiled: CooCompiledPipeline,
        nodesByName: Map<String, CooPipelineNode>,
        outputPasses: Map<String, String>,
        defaults: PostEffectParamsBuilder
    ): PostEffectInput {
        val input = this.input as CooPipelineInputPort
        return when (val source = output) {
            CooPipelineTextureSource.SceneColor -> input.input(PostEffectInputSource.SCENE_COLOR)
            CooPipelineTextureSource.SceneDepth -> input.input(PostEffectInputSource.SCENE_DEPTH)
            CooPipelineTextureSource.Mask -> input.input(PostEffectInputSource.MASK)
            CooPipelineTextureSource.Bloom -> input.input(PostEffectInputSource.BRIGHT_COLOR)
            CooPipelineTextureSource.Temporary -> input.inputSceneResource(
                RenderSceneTargets.TEMPORARY
            )
            is CooPipelineTextureSource.Texture -> {
                defaults.resource(input.sampler, source.texture)
                input.input(PostEffectInputSource.CUSTOM_TEXTURE)
            }
            is CooPipelineTextureSource.Parameter -> input.input(PostEffectInputSource.CUSTOM_TEXTURE)
            CooPipelineTextureSource.BlockAtlas -> {
                defaults.resource(
                    input.sampler,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")
                )
                input.input(PostEffectInputSource.CUSTOM_TEXTURE)
            }
            is CooPipelineTextureSource.FramebufferColor -> {
                val writer = compiled.attachments.singleOrNull { attachment ->
                    attachment.framebuffer == source.target && attachment.output.attachment == source.attachment
                }
                val writerNode = writer?.let { nodesByName[it.output.node] }
                if (writerNode != null && writerNode.kind != CooPipelineNodeKind.WORLD) {
                    PostEffectInput(
                        samplerName = input.sampler,
                        source = PostEffectInputSource.PASS_OUTPUT,
                        optional = input.optional,
                        sourcePassName = requireNotNull(outputPasses[writerNode.name]) {
                            "Named framebuffer writer '${writerNode.name}' has no compiled output pass"
                        },
                        sourcePassAttachment = source.attachment,
                        textureSlot = input.textureSlot
                    )
                } else {
                    input.inputSceneResource(source.target, attachment = source.attachment)
                }
            }
            is CooPipelineOutputPort -> {
                val producer = requireNotNull(nodesByName[source.node])
                if (producer.kind == CooPipelineNodeKind.FULLSCREEN) {
                    PostEffectInput(
                        samplerName = input.sampler,
                        source = PostEffectInputSource.PASS_OUTPUT,
                        optional = input.optional,
                        sourcePassName = requireNotNull(outputPasses[source.node]) {
                            "Pipeline node '${source.node}' has no compiled output pass"
                        },
                        sourcePassAttachment = source.attachment,
                        textureSlot = input.textureSlot
                    )
                } else if (producer.kind == CooPipelineNodeKind.PING_PONG) {
                    PostEffectInput(
                        samplerName = input.sampler,
                        source = PostEffectInputSource.PASS_OUTPUT,
                        optional = input.optional,
                        sourcePassName = requireNotNull(outputPasses[source.node]) {
                            "Ping-pong node '${source.node}' has no compiled output pass"
                        },
                        sourcePassAttachment = source.attachment,
                        textureSlot = input.textureSlot
                    )
                } else {
                    val attachment = requireNotNull(compiled.attachment(source)) {
                        "World output '${source.node}.${source.name}' is connected but has no compiled FBO attachment"
                    }
                    input.inputSceneResource(
                        attachment.framebuffer,
                        attachment = source.attachment
                    )
                }
            }
        }
    }

    private fun CooPipelineNode.toPostPass(
        name: String,
        shader: CooPipelineShader.Stages,
        inputs: List<PostEffectInput>,
        output: PostEffectOutput,
        framebuffer: ResourceLocation?,
        iteration: CooPipelineIteration? = null,
        outputTargetKey: String? = framebuffer?.toString(),
        reuseOutputTarget: Boolean = false
    ): PostEffectPass {
        val staticUniforms = uniforms.map { (uniformName, provider) ->
            PostEffectUniform(uniformName) { instance ->
                instance.params[uniformName] ?: provider.resolve(instance.subject).toPostValue()
            }
        }
        val iterationUniforms = if (iteration == null) {
            emptyList()
        } else {
            requireNotNull(pingPong).uniforms.map { (uniformName, provider) ->
                PostEffectUniform(uniformName) { instance ->
                    instance.params[uniformName] ?: provider.resolve(iteration).toPostValue()
                }
            }
        }
        return PostEffectPass(
            name = name,
            vertex = shader.vertex,
            fragment = shader.fragment,
            inputs = inputs,
            output = output,
            uniforms = staticUniforms + iterationUniforms,
            outputTargetId = framebuffer,
            outputTargetKey = outputTargetKey,
            colorAttachmentCount = outputs.maxOfOrNull(CooPipelineOutputPort::attachment)?.plus(1) ?: 1,
            reuseOutputTarget = reuseOutputTarget
        )
    }

    private fun CooPipelineInputPort.input(source: PostEffectInputSource): PostEffectInput {
        return PostEffectInput(
            samplerName = sampler,
            source = source,
            optional = optional,
            textureSlot = textureSlot
        )
    }

    private fun CooPipelineInputPort.inputSceneResource(
        resource: ResourceLocation,
        channel: PostEffectResourceChannel = PostEffectResourceChannel.COLOR,
        attachment: Int = 0
    ): PostEffectInput {
        return PostEffectInput(
            samplerName = sampler,
            source = PostEffectInputSource.SCENE_RESOURCE,
            optional = optional,
            sourceResourceId = resource,
            sourceResourceAttachment = attachment,
            sourceResourceChannel = channel,
            textureSlot = textureSlot
        )
    }

    private fun CooPipelineTarget.toPostOutput(): PostEffectOutput {
        return when (this) {
            CooPipelineTarget.FinalScreen -> PostEffectOutput.FINAL_SCREEN
            CooPipelineTarget.Mask -> PostEffectOutput.MASK
            CooPipelineTarget.Bloom -> PostEffectOutput.BLOOM
            CooPipelineTarget.Temporary,
            CooPipelineTarget.World,
            is CooPipelineTarget.FramebufferColor -> PostEffectOutput.TEMPORARY
        }
    }

    private fun CooUniformValue.toPostValue(): PostEffectParamValue {
        return when (this) {
            is CooUniformValue.FloatValue -> PostEffectParamValue.FloatValue(value)
            is CooUniformValue.IntValue -> PostEffectParamValue.IntValue(value)
            is CooUniformValue.Vec2Value -> PostEffectParamValue.Vec2Value(x, y)
            is CooUniformValue.Vec3Value -> PostEffectParamValue.Vec3Value(x.toDouble(), y.toDouble(), z.toDouble())
            is CooUniformValue.Vec4Value -> PostEffectParamValue.ColorValue(x, y, z, w)
        }
    }
}
