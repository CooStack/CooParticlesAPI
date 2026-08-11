package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
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
import cn.coostack.cooparticlesapi.renderer.shader.SimpleShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooProgramUniformAccess
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.vertex.DynamicVertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL33.GL_LEQUAL
import org.lwjgl.opengl.GL33.GL_LINES
import org.lwjgl.opengl.GL33.GL_LINE_WIDTH
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_QUADS
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
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetFloat
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glIsProgram
import org.lwjgl.opengl.GL33.glLineWidth
import org.lwjgl.opengl.GL33.glUseProgram

object OpenGlRenderEntityModelExecutor : RenderEntityModelExecutor {
    private val defaultStages = CooPipelineShader.Stages(
        vertex = shaderId("core/vertex/render_entity_model.vsh"),
        fragment = shaderId("core/fragment/render_entity_model.fsh")
    )
    private val stagePrograms = LinkedHashMap<CooPipelineShader.Stages, CooShaderProgram>()
    private val corePrograms = LinkedHashMap<ResourceLocation, ShaderInstance>()
    private val customTextures = LinkedHashMap<ResourceLocation, IdentifierTexture>()
    private val failedStagePrograms = linkedSetOf<CooPipelineShader.Stages>()
    private val failedCorePrograms = linkedSetOf<ResourceLocation>()
    private val warnedSkippedCorePrograms = linkedSetOf<ResourceLocation>()
    private val warnedInputs = linkedSetOf<String>()
    private var buffer: DynamicVertexBuffer? = null

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
        corePrograms.values.forEach(ShaderInstance::close)
        corePrograms.clear()
        customTextures.values.forEach(IdentifierTexture::release)
        customTextures.clear()
        failedStagePrograms.clear()
        failedCorePrograms.clear()
        warnedSkippedCorePrograms.clear()
        warnedInputs.clear()
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
        id: ResourceLocation,
        primitives: List<RenderEntityModelPrimitive>,
        input: RenderInput<*>,
        bindings: List<TextureBinding>
    ): Boolean {
        val shader = coreProgram(id) ?: return false
        val previousProgram = glGetInteger(GL_CURRENT_PROGRAM)
        shader.apply()
        val activeProgram = glGetInteger(GL_CURRENT_PROGRAM)
        if (activeProgram <= 0 ||
            CooParticlesAPIClient.checkIrisShaderPackUsed() && activeProgram == previousProgram
        ) {
            if (warnedSkippedCorePrograms.add(id)) {
                CooParticlesConstants.logger.warn(
                    "RenderEntity core shader {} was not activated by the current renderer; using the default model shader",
                    id
                )
            }
            shader.clear()
            restoreProgram(previousProgram)
            return false
        }
        return try {
            val uniforms = ActiveProgramUniforms(activeProgram)
            uploadUniforms(uniforms, input)
            withBoundTextures(bindings, uniforms) {
                drawPrimitives(primitives)
            }
            true
        } finally {
            shader.clear()
            restoreProgram(previousProgram)
        }
    }

    /** 上传框架矩阵、共享默认值和当前 world 节点的实体 uniform。 */
    private fun uploadUniforms(uniforms: CooProgramUniformAccess, input: RenderInput<*>) {
        val modelView = Matrix4f(input.viewMatrix).mul(input.modelMatrix)
        uniforms.setMatrix4("projMat", input.projMatrix)
        uniforms.setMatrix4("ProjMat", input.projMatrix)
        uniforms.setMatrix4("viewMat", input.viewMatrix)
        uniforms.setMatrix4("transMat", input.modelMatrix)
        uniforms.setMatrix4("ModelViewMat", modelView)
        uniforms.setFloat("intensity", 1F)
        uniforms.setFloat("BloomIntensity", 1F)
        input.node.uniforms.forEach { (name, provider) ->
            uploadUniform(uniforms, name, provider.resolve(input.entity))
        }
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
            bindings += TextureBinding(port.sampler, port.textureSlot, textureId)
        }
        return bindings
    }

    private fun resolveTexture(source: CooPipelineTextureSource, input: RenderInput<*>): Int? {
        return when (source) {
            is CooPipelineTextureSource.Texture -> customTexture(source.texture)
            CooPipelineTextureSource.BlockAtlas -> Minecraft.getInstance().textureManager
                .getTexture(TextureAtlas.LOCATION_BLOCKS).id
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

    private fun customTexture(id: ResourceLocation): Int {
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
        try {
            bindings.forEach { binding ->
                glActiveTexture(GL_TEXTURE0 + binding.slot)
                previousBindings.putIfAbsent(binding.slot, glGetInteger(GL_TEXTURE_BINDING_2D))
                glBindTexture(GL_TEXTURE_2D, binding.textureId)
                uniforms.setInt(binding.sampler, binding.slot)
            }
            draw()
        } finally {
            previousBindings.entries.reversed().forEach { (slot, textureId) ->
                glActiveTexture(GL_TEXTURE0 + slot)
                glBindTexture(GL_TEXTURE_2D, textureId)
            }
            glActiveTexture(previousActiveTexture)
        }
    }

    private fun drawPrimitives(primitives: List<RenderEntityModelPrimitive>) {
        val vertexBuffer = buffer()
        primitives.forEach { primitive ->
            val vertices = primitive.vertices.map { vertex ->
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

    private fun coreProgram(id: ResourceLocation): ShaderInstance? {
        corePrograms[id]?.let { return it }
        if (id in failedCorePrograms) return null
        return try {
            val minecraftPath = "${id.namespace}/${id.path}"
            ShaderInstance(
                Minecraft.getInstance().resourceManager,
                minecraftPath,
                VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("UV0", VertexFormatElement.UV0)
                    .build()
            ).also { created ->
                IrisCompat.markUnskippable(created)
                corePrograms[id] = created
            }
        } catch (error: Exception) {
            failedCorePrograms += id
            CooParticlesConstants.logger.error(
                "Failed to load RenderEntity core shader {}; using the default model shader",
                id,
                error
            )
            null
        }
    }

    private fun restoreProgram(program: Int) {
        if (program > 0 && glIsProgram(program)) glUseProgram(program) else glUseProgram(0)
    }

    private fun buffer(): DynamicVertexBuffer {
        return buffer ?: DynamicVertexBuffer().also { created ->
            created.init()
            buffer = created
        }
    }

    private fun withWorldModelState(block: () -> Unit) {
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullEnabled = glIsEnabled(GL_CULL_FACE)
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val depthFunc = glGetInteger(GL_DEPTH_FUNC)
        val blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB)
        val blendDstRgb = glGetInteger(GL_BLEND_DST_RGB)
        val blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA)
        val blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA)
        val lineWidth = glGetFloat(GL_LINE_WIDTH)
        glEnable(GL_BLEND)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glLineWidth(1F)
        try {
            block()
        } finally {
            glLineWidth(lineWidth)
            glDepthMask(depthMask)
            glDepthFunc(depthFunc)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
        }
    }

    private fun RenderEntityModelPrimitiveMode.toGlMode(): Int {
        return when (this) {
            RenderEntityModelPrimitiveMode.LINES -> GL_LINES
            RenderEntityModelPrimitiveMode.TRIANGLES -> GL_TRIANGLES
            RenderEntityModelPrimitiveMode.QUADS -> GL_QUADS
        }
    }

    private class ActiveProgramUniforms(
        override var program: Int
    ) : CooProgramUniformAccess

    private data class TextureBinding(
        val sampler: String,
        val slot: Int,
        val textureId: Int
    )

    private fun shaderId(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
