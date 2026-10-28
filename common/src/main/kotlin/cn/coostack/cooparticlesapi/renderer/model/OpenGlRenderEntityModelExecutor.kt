package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import cn.coostack.cooparticlesapi.renderer.backend.glTextureId
import cn.coostack.cooparticlesapi.renderer.backend.glSamplerId
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineShader
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTextureSource
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.pipeline.setUniform
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderPhase
import cn.coostack.cooparticlesapi.renderer.state.CooGLSLStateManager
import cn.coostack.cooparticlesapi.renderer.shader.SimpleShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.CooCoreShaderDefinition
import cn.coostack.cooparticlesapi.renderer.shader.api.CooProgramUniformAccess
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.vertex.DynamicVertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferCache
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferFieldDescriptor
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferFieldType
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferObject
import com.google.gson.JsonObject
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_GEQUAL
import org.lwjgl.opengl.GL33.GL_LINES
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_TEXTURE0
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.GL_TRIANGLES
import org.lwjgl.opengl.GL33.glActiveTexture
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glLineWidth
import org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_START
import org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_SIZE
import org.lwjgl.opengl.GL33.GL_UNIFORM_BLOCK_DATA_SIZE
import org.lwjgl.opengl.GL33.GL_INVALID_INDEX
import org.lwjgl.opengl.GL33.GL_FUNC_ADD
import org.lwjgl.opengl.GL33.GL_FUNC_SUBTRACT
import org.lwjgl.opengl.GL33.GL_FUNC_REVERSE_SUBTRACT
import org.lwjgl.opengl.GL33.GL_MIN
import org.lwjgl.opengl.GL33.GL_MAX
import org.lwjgl.opengl.GL33.GL_ZERO
import org.lwjgl.opengl.GL33.GL_SRC_COLOR
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_COLOR
import org.lwjgl.opengl.GL33.GL_DST_COLOR
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_DST_COLOR
import org.lwjgl.opengl.GL33.GL_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_DST_ALPHA
import org.lwjgl.opengl.GL33.glBindBuffer
import org.lwjgl.opengl.GL33.glBindBufferBase
import org.lwjgl.opengl.GL33.glBindBufferRange
import org.lwjgl.opengl.GL33.glGetIntegeri
import org.lwjgl.opengl.GL33.glGetInteger64i
import org.lwjgl.opengl.GL33.glGetUniformBlockIndex
import org.lwjgl.opengl.GL33.glGetActiveUniformBlocki
import org.lwjgl.opengl.GL33.glUniformBlockBinding
import org.lwjgl.opengl.GL33.glBlendEquationSeparate
import org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING
import org.lwjgl.opengl.GL33.glBindSampler

/** OpenGL 后端的模型执行器；Core JSON 与显式阶段均使用库自身拥有的程序。 */
object OpenGlRenderEntityModelExecutor : RenderEntityModelExecutor {
    private val defaultStages = CooPipelineShader.Stages(
        vertex = shaderId("core/vertex/render_entity_model.vsh"),
        fragment = shaderId("core/fragment/render_entity_model.fsh")
    )
    private val stagePrograms = LinkedHashMap<CooPipelineShader.Stages, CooShaderProgram>()
    private val corePrograms = LinkedHashMap<Identifier, CoreProgram>()
    private val customTextures = LinkedHashMap<Identifier, IdentifierTexture>()
    private val failedStagePrograms = linkedSetOf<CooPipelineShader.Stages>()
    private val failedCorePrograms = linkedSetOf<Identifier>()
    private val warnedInputs = linkedSetOf<String>()
    private var buffer: DynamicVertexBuffer? = null
    /** 只读单位矩阵值在每次绘制的默认纹理变换中复用。 */
    private val identityMatrixValue = CooUniformValue.MatValue(Matrix4f())
    /** 与原版 Projection std140 块同布局，由本执行器拥有独立缓冲。 */
    private val projectionLayout = ShaderBufferLayout<Map<String, CooUniformValue>>(
        "CooModelProjection",
        listOf(ShaderBufferFieldDescriptor("ProjMat", ShaderBufferFieldType.MAT4) { values ->
            matrixValue(values, "ProjMat")
        }),
    )
    /** 与原版 DynamicTransforms std140 块同布局，兼容普通 uniform 与命令式管线的源码。 */
    private val transformsLayout = ShaderBufferLayout<Map<String, CooUniformValue>>(
        "CooModelDynamicTransforms",
        listOf(
            ShaderBufferFieldDescriptor("ModelViewMat", ShaderBufferFieldType.MAT4) { values ->
                matrixValue(values, "ModelViewMat")
            },
            ShaderBufferFieldDescriptor("TextureMat", ShaderBufferFieldType.MAT4) { values ->
                matrixValue(values, "TextureMat")
            },
            ShaderBufferFieldDescriptor("ColorModulator", ShaderBufferFieldType.VEC4) { values ->
                val value = values.getValue("ColorModulator") as CooUniformValue.Vec4Value
                floatArrayOf(value.x, value.y, value.z, value.w)
            },
            ShaderBufferFieldDescriptor("ModelOffset", ShaderBufferFieldType.VEC3) { values ->
                val value = values.getValue("ModelOffset") as CooUniformValue.Vec3Value
                floatArrayOf(value.x, value.y, value.z)
            },
        ),
    )

    /**
     * 执行 `OpenGlRenderEntityModelExecutor` 的 `draw` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`draw(model = model, input = input)`。
     *
     * @param model 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param input 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    override fun draw(model: RenderEntityModel, input: RenderInput<*>) {
        val visiblePrimitives = model.primitives.filter { it.vertices.isNotEmpty() }
        if (visiblePrimitives.isEmpty()) return
        OpenGlRenderTargetBridge.requireOpenGlBackend()

        val bindings = resolveBindings(input)
        withWorldModelState {
            if (bindings == null) {
                drawWithStages(defaultStages, visiblePrimitives, input, emptyList())
                return@withWorldModelState
            }
            when (val shader = input.node.shader) {
                null -> drawWithStages(defaultStages, visiblePrimitives, input, bindings)
                is CooPipelineShader.Stages -> {
                    if (!drawWithStages(shader, visiblePrimitives, input, bindings)) {
                        drawWithStages(defaultStages, visiblePrimitives, input, emptyList())
                    }
                }
                is CooPipelineShader.Core -> {
                    if (!drawWithCore(shader.id, visiblePrimitives, input, bindings)) {
                        drawWithStages(defaultStages, visiblePrimitives, input, emptyList())
                    }
                }
            }
        }
    }

    /**
     * 释放 `OpenGlRenderEntityModelExecutor` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    fun release() {
        buffer?.release()
        buffer = null
        stagePrograms.values.forEach(CooShaderProgram::release)
        stagePrograms.clear()
        corePrograms.values.forEach { it.shader.release() }
        corePrograms.clear()
        customTextures.values.forEach(IdentifierTexture::release)
        customTextures.clear()
        failedStagePrograms.clear()
        failedCorePrograms.clear()
        warnedInputs.clear()
        ShaderBufferCache.release(projectionLayout)
        ShaderBufferCache.release(transformsLayout)
    }

    private fun drawWithStages(
        stages: CooPipelineShader.Stages,
        primitives: List<RenderEntityModelPrimitive>,
        input: RenderInput<*>,
        bindings: List<TextureBinding>
    ): Boolean {
        val shader = stageProgram(stages) ?: return false
        shader.use()
        return try {
            val uniforms = ActiveProgramUniforms(glGetInteger(GL_CURRENT_PROGRAM))
            uploadUniforms(uniforms, input)
            withBoundTextures(bindings, uniforms) {
                drawPrimitives(primitives)
            }
            true
        } finally {
            shader.reset()
        }
    }

    private fun drawWithCore(
        id: Identifier,
        primitives: List<RenderEntityModelPrimitive>,
        input: RenderInput<*>,
        bindings: List<TextureBinding>
    ): Boolean {
        val core = coreProgram(id) ?: return false
        val shader = core.shader
        shader.use()
        return try {
            val uniforms = ActiveProgramUniforms(shader.program)
            core.definition.samplers.forEachIndexed { slot, name -> uniforms.setInt(name, slot) }
            val values = uploadUniforms(uniforms, input, core.definition.uniforms)
            val explicitBindings = core.definition.textures.map { (name, texture) ->
                TextureBinding(name, core.definition.samplers.indexOf(name), customTexture(texture), customTextures.getValue(texture).samplerID)
            }
            val combined = (explicitBindings + bindings).associateBy(TextureBinding::sampler).values.toList()
            withUniformBlocks(core.blocks, values) {
                applyCoreBlend(core.definition.blend)
                withBoundTextures(combined, uniforms) {
                    drawPrimitives(primitives)
                }
            }
            true
        } finally {
            shader.reset()
        }
    }

    /** 上传框架矩阵、共享默认值和当前 world 节点的实体 uniform。 */
    private fun uploadUniforms(
        uniforms: CooProgramUniformAccess,
        input: RenderInput<*>,
        defaults: Map<String, CooUniformValue> = emptyMap(),
    ): Map<String, CooUniformValue> {
        val modelView = Matrix4f(input.viewMatrix).mul(input.modelMatrix)
        val values = LinkedHashMap(defaults)
        values["projMat"] = CooUniformValue.MatValue(input.projMatrix)
        values["ProjMat"] = CooUniformValue.MatValue(input.projMatrix)
        values["viewMat"] = CooUniformValue.MatValue(input.viewMatrix)
        values["transMat"] = CooUniformValue.MatValue(input.modelMatrix)
        values["ModelViewMat"] = CooUniformValue.MatValue(modelView)
        values.putIfAbsent("TextureMat", identityMatrixValue)
        values.putIfAbsent("ColorModulator", CooUniformValue.Vec4Value(1F, 1F, 1F, 1F))
        values.putIfAbsent("ModelOffset", CooUniformValue.Vec3Value(0F, 0F, 0F))
        values["intensity"] = CooUniformValue.FloatValue(1F)
        values["BloomIntensity"] = CooUniformValue.FloatValue(1F)
        input.node.uniforms.forEach { (name, provider) ->
            values[name] = provider.resolve(input.entity)
        }
        values.forEach { (name, value) -> uploadUniform(uniforms, name, value) }
        return values
    }

    private fun uploadUniform(uniforms: CooProgramUniformAccess, name: String, value: CooUniformValue) {
        uniforms.setUniform(name, value)
    }

    private fun resolveBindings(input: RenderInput<*>): List<TextureBinding>? {
        val lines = input.pipeline.lines.filter { line ->
            (line.input as? CooPipelineInputPort)?.node == input.node.name
        }
        val bindings = ArrayList<TextureBinding>(lines.size)
        lines.forEach { line ->
            val port = line.input as CooPipelineInputPort
            val textureId = resolveTexture(line.output, input)
            if (textureId == null || textureId <= 0) {
                if (port.optional) return@forEach
                warnMissingInput(input, port, line.output)
                return null
            }
            val samplerId = when (val source = line.output) {
                is CooPipelineTextureSource.Texture -> customTextures.getValue(source.texture).samplerID
                CooPipelineTextureSource.BlockAtlas -> Minecraft.getInstance().textureManager
                    .getTexture(TextureAtlas.LOCATION_BLOCKS).sampler.glSamplerId
                else -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR).glSamplerId
            }
            bindings += TextureBinding(port.sampler, port.textureSlot, textureId, samplerId)
        }
        return bindings
    }

    private fun resolveTexture(source: CooPipelineTextureSource, input: RenderInput<*>): Int? {
        return when (source) {
            is CooPipelineTextureSource.Texture -> customTexture(source.texture)
            CooPipelineTextureSource.BlockAtlas -> Minecraft.getInstance().textureManager
                .getTexture(TextureAtlas.LOCATION_BLOCKS).texture.glTextureId
            CooPipelineTextureSource.SceneColor -> if (input.phase == RenderPhase.OFFSCREEN) {
                ClientRenderPipelineManager.currentSceneColorTextureId()
            } else {
                null
            }
            CooPipelineTextureSource.SceneDepth -> if (input.phase == RenderPhase.OFFSCREEN) {
                ClientRenderPipelineManager.currentSceneDepthTextureId()
            } else {
                null
            }
            CooPipelineTextureSource.SceneDepthNoHand,
            CooPipelineTextureSource.TerrainDepth,
             CooPipelineTextureSource.TerrainOpaqueDepth,
             CooPipelineTextureSource.TerrainTranslucentDepthBefore,
             CooPipelineTextureSource.TerrainTranslucentDepthAfter -> null
            is CooPipelineTextureSource.FramebufferColor -> ClientRenderPipelineManager.currentSceneResourceTextureId(
                source.target,
                source.attachment
            )
            CooPipelineTextureSource.Mask -> ClientRenderPipelineManager.currentSceneResourceTextureId(
                RenderSceneTargets.MASK
            )
            CooPipelineTextureSource.Temporary -> ClientRenderPipelineManager.currentSceneResourceTextureId(
                RenderSceneTargets.TEMPORARY
            )
            CooPipelineTextureSource.Bloom -> ClientRenderPipelineManager.currentSceneResourceTextureId(
                RenderSceneTargets.BLOOM
            )
            is CooPipelineTextureSource.Parameter -> {
                (input.pipeline.resolveUniform(input.node.name, source.name, input.entity) as? CooUniformValue.IntValue)?.value
            }
            is CooPipelineOutputPort -> null
        }
    }

    private fun customTexture(id: Identifier): Int {
        val texture = customTextures.getOrPut(id) { IdentifierTexture(id) }
        if (texture.textureID() <= 0) texture.init()
        return texture.textureID()
    }

    private fun warnMissingInput(
        input: RenderInput<*>,
        port: CooPipelineInputPort,
        source: CooPipelineTextureSource
    ) {
        val key = "${input.pipeline.id}:${input.node.name}:${port.sampler}:$source"
        if (!warnedInputs.add(key)) return
        CooParticlesConstants.logger.warn(
            "RenderEntity pipeline {} node {} cannot resolve required input {} from {}; using the default model shader",
            input.pipeline.id,
            input.node.name,
            port.sampler,
            source
        )
    }

    private fun withBoundTextures(
        bindings: List<TextureBinding>,
        uniforms: CooProgramUniformAccess,
        draw: () -> Unit
    ) {
        if (bindings.isEmpty()) {
            draw()
            return
        }
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val previousBindings = LinkedHashMap<Int, Int>()
        val previousSamplers = LinkedHashMap<Int, Int>()
        try {
            bindings.forEach { binding ->
                glActiveTexture(GL_TEXTURE0 + binding.slot)
                previousBindings.putIfAbsent(binding.slot, glGetInteger(GL_TEXTURE_BINDING_2D))
                previousSamplers.putIfAbsent(binding.slot, glGetIntegeri(GL_SAMPLER_BINDING, binding.slot))
                glBindTexture(GL_TEXTURE_2D, binding.textureId)
                glBindSampler(binding.slot, binding.samplerId)
                uniforms.setInt(binding.sampler, binding.slot)
            }
            draw()
        } finally {
            previousBindings.entries.reversed().forEach { (slot, textureId) ->
                glActiveTexture(GL_TEXTURE0 + slot)
                glBindTexture(GL_TEXTURE_2D, textureId)
                glBindSampler(slot, previousSamplers.getValue(slot))
            }
            glActiveTexture(previousActiveTexture)
        }
    }

    private fun drawPrimitives(primitives: List<RenderEntityModelPrimitive>) {
        val vertexBuffer = buffer()
        primitives.forEach { primitive ->
            val sourceVertices = if (primitive.primitiveMode == RenderEntityModelPrimitiveMode.QUADS) {
                require(primitive.vertices.size % 4 == 0) { "模型四边形顶点数量必须为四的倍数" }
                primitive.vertices.chunked(4).flatMap { listOf(it[0], it[1], it[2], it[2], it[3], it[0]) }
            } else primitive.vertices
            val vertices = sourceVertices.map { vertex ->
                VertexData(vertex.position, vertex.color, vertex.uv)
            }
            vertexBuffer.drawMode = primitive.primitiveMode.toGlMode()
            vertexBuffer.setVertexes(vertices, CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT)
            vertexBuffer.draw()
        }
    }

    private fun stageProgram(stages: CooPipelineShader.Stages): CooShaderProgram? {
        stagePrograms[stages]?.let { return it }
        if (stages in failedStagePrograms) return null
        val vertex = IdentifierShader(stages.vertex ?: defaultStages.vertex!!, GlShaderType.VERTEX)
        val fragment = IdentifierShader(stages.fragment, GlShaderType.FRAGMENT)
        val created = SimpleShaderProgram(vertexShader = vertex, fragmentShader = fragment)
        return try {
            created.init()
            stagePrograms[stages] = created
            created
        } catch (error: Exception) {
            created.release()
            if (vertex.shaderID() > 0) vertex.deleteShader()
            if (fragment.shaderID() > 0) fragment.deleteShader()
            failedStagePrograms += stages
            CooParticlesConstants.logger.error(
                "Failed to load RenderEntity shader stages vertex={} fragment={}; using the default model shader",
                stages.vertex,
                stages.fragment,
                error
            )
            null
        }
    }

    /** 保留 core JSON 阶段选择和默认参数，程序生命周期与阶段缓存独立。 */
    private fun coreProgram(id: Identifier): CoreProgram? {
        corePrograms[id]?.let { return it }
        if (id in failedCorePrograms) return null
        var shader: CooShaderProgram? = null
        return try {
            val definition = CooCoreShaderDefinition.load(Minecraft.getInstance().resourceManager, id)
            val created = SimpleShaderProgram(
                IdentifierShader(definition.vertex, GlShaderType.VERTEX),
                IdentifierShader(definition.fragment, GlShaderType.FRAGMENT),
                attributeLocationsInternal = mapOf("Position" to 0, "Color" to 1, "UV0" to 2),
            )
            shader = created
            created.init()
            val blocks = listOf("Projection" to projectionLayout, "DynamicTransforms" to transformsLayout).mapNotNull { (name, layout) ->
                val index = glGetUniformBlockIndex(created.program, name)
                if (index == GL_INVALID_INDEX) return@mapNotNull null
                val buffer = ShaderBufferCache.getOrCreate(layout)
                require(glGetActiveUniformBlocki(created.program, index, GL_UNIFORM_BLOCK_DATA_SIZE) <= layout.byteSize()) {
                    "Core shader $id 的 $name 布局与 26.3 矩阵块不兼容"
                }
                glUniformBlockBinding(created.program, index, requireNotNull(buffer.layout.assignedBinding))
                buffer
            }
            CoreProgram(created, definition, blocks).also { corePrograms[id] = it }
        } catch (error: Exception) {
            shader?.release()
            failedCorePrograms += id
            CooParticlesConstants.logger.error(
                "Failed to load RenderEntity core shader {}; using the default model shader",
                id,
                error
            )
            null
        }
    }

    /** 上传框架矩阵块时保存完整 UBO 范围绑定，结束后恢复调用方和 Iris 的状态。 */
    private fun withUniformBlocks(
        blocks: List<ShaderBufferObject<Map<String, CooUniformValue>>>,
        values: Map<String, CooUniformValue>,
        draw: () -> Unit,
    ) {
        val generic = glGetInteger(GL_UNIFORM_BUFFER_BINDING)
        val previous = blocks.map { buffer ->
            val binding = requireNotNull(buffer.layout.assignedBinding)
            UniformBufferBinding(
                binding,
                glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, binding),
                glGetInteger64i(GL_UNIFORM_BUFFER_START, binding),
                glGetInteger64i(GL_UNIFORM_BUFFER_SIZE, binding),
            )
        }
        try {
            blocks.forEach { it.upload(values) }
            draw()
        } finally {
            previous.forEach { binding ->
                if (binding.buffer > 0 && binding.size > 0L) {
                    glBindBufferRange(GL_UNIFORM_BUFFER, binding.index, binding.buffer, binding.offset, binding.size)
                } else glBindBufferBase(GL_UNIFORM_BUFFER, binding.index, binding.buffer)
            }
            glBindBuffer(GL_UNIFORM_BUFFER, generic)
        }
    }

    /** JSON 混合参数只覆盖本节点，外层状态作用域在绘制结束后恢复。 */
    private fun applyCoreBlend(blend: JsonObject?) {
        if (blend == null) {
            glDisable(GL_BLEND)
            return
        }
        glEnable(GL_BLEND)
        val operation = when (blend.getAsJsonPrimitive("func")?.asString ?: "add") {
            "add" -> GL_FUNC_ADD
            "subtract" -> GL_FUNC_SUBTRACT
            "reversesubtract" -> GL_FUNC_REVERSE_SUBTRACT
            "min" -> GL_MIN
            "max" -> GL_MAX
            else -> error("不支持的 Core 混合运算：$blend")
        }
        glBlendEquationSeparate(operation, operation)
        val source = blend.getAsJsonPrimitive("srcrgb")?.asString ?: "1"
        val destination = blend.getAsJsonPrimitive("dstrgb")?.asString ?: "0"
        glBlendFuncSeparate(
            blendFactor(source), blendFactor(destination),
            blendFactor(blend.getAsJsonPrimitive("srcalpha")?.asString ?: source),
            blendFactor(blend.getAsJsonPrimitive("dstalpha")?.asString ?: destination),
        )
    }

    /** 保留旧 core JSON 接受的颜色与透明度因子名称，不静默替换未知值。 */
    private fun blendFactor(value: String): Int = when (value.lowercase().replace("_", "")) {
        "0", "zero" -> GL_ZERO
        "1", "one" -> GL_ONE
        "srccolor" -> GL_SRC_COLOR
        "1-srccolor", "oneminussrccolor" -> GL_ONE_MINUS_SRC_COLOR
        "dstcolor" -> GL_DST_COLOR
        "1-dstcolor", "oneminusdstcolor" -> GL_ONE_MINUS_DST_COLOR
        "srcalpha" -> GL_SRC_ALPHA
        "1-srcalpha", "oneminussrcalpha" -> GL_ONE_MINUS_SRC_ALPHA
        "dstalpha" -> GL_DST_ALPHA
        "1-dstalpha", "oneminusdstalpha" -> GL_ONE_MINUS_DST_ALPHA
        else -> error("不支持的 Core 混合因子：$value")
    }

    /** 标准矩阵块使用四乘四浮点矩阵，错误节点参数必须显式失败。 */
    private fun matrixValue(values: Map<String, CooUniformValue>, name: String): FloatArray {
        val value = values.getValue(name) as? CooUniformValue.MatValue
            ?: error("矩阵块参数 $name 必须为浮点矩阵")
        require(value.columns == 4 && value.rows == 4) { "矩阵块参数 $name 必须为 mat4" }
        return value.components.toFloatArray()
    }

    private fun buffer(): DynamicVertexBuffer {
        return buffer ?: DynamicVertexBuffer().also { created ->
            created.init()
            buffer = created
        }
    }

    private fun withWorldModelState(block: () -> Unit) {
        CooGLSLStateManager.useState {
            glEnable(GL_BLEND)
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_GEQUAL)
            glDepthMask(false)
            glDisable(GL_CULL_FACE)
            glLineWidth(1F)
            block()
        }
    }

    private fun RenderEntityModelPrimitiveMode.toGlMode(): Int {
        return when (this) {
            RenderEntityModelPrimitiveMode.LINES -> GL_LINES
            RenderEntityModelPrimitiveMode.TRIANGLES -> GL_TRIANGLES
            RenderEntityModelPrimitiveMode.QUADS -> GL_TRIANGLES
        }
    }

    /** 包装当前程序的普通 uniform 访问，不改变程序所有权。 */
    private class ActiveProgramUniforms(
        override var program: Int
    ) : CooProgramUniformAccess

    /**
     * 库自身拥有的 core 程序与其 JSON 语义。
     * @property shader 可释放的实际程序
     * @property definition 阶段、默认参数与纹理声明
     * @property blocks 当前程序实际消费的标准矩阵块
     */
    private data class CoreProgram(
        val shader: CooShaderProgram,
        val definition: CooCoreShaderDefinition,
        val blocks: List<ShaderBufferObject<Map<String, CooUniformValue>>>,
    )

    /**
     * 绘制前保存的索引 UBO 状态。
     * @property index 绑定点
     * @property buffer 原缓冲对象名
     * @property offset 原范围起点，单位字节
     * @property size 原范围长度，单位字节
     */
    private data class UniformBufferBinding(val index: Int, val buffer: Int, val offset: Long, val size: Long)

    /**
     * 节点本次绘制的纹理绑定。
     * @property sampler 程序中的采样器名称
     * @property slot OpenGL 纹理单元
     * @property textureId 活动后端的纹理对象名
     * @property samplerId 由原纹理或原版缓存拥有的采样器对象名
     */
    private data class TextureBinding(
        val sampler: String,
        val slot: Int,
        val textureId: Int,
        val samplerId: Int,
    )

    private fun shaderId(path: String): Identifier {
        return Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
