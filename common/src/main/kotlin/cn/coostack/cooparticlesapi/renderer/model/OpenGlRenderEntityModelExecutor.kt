package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.shader.SimpleShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.vertex.DynamicVertexBuffer
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL33.GL_LEQUAL
import org.lwjgl.opengl.GL33.GL_LINES
import org.lwjgl.opengl.GL33.GL_LINE_WIDTH
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_TRIANGLES
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetFloat
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glLineWidth

object OpenGlRenderEntityModelExecutor : RenderEntityModelExecutor {
    private var program: CooShaderProgram? = null
    private var buffer: DynamicVertexBuffer? = null

    override fun draw(model: RenderEntityModel, input: LocalRenderInput<*>) {
        val visiblePrimitives = model.primitives.filter { it.pipe.postEffectType == null && it.vertices.isNotEmpty() }
        if (visiblePrimitives.isEmpty()) {
            return
        }
        val shader = program()
        val vertexBuffer = buffer()
        withWorldModelState {
            shader.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                visiblePrimitives.forEach { primitive ->
                    val vertices = primitive.vertices.map { vertex ->
                        VertexData(vertex.position, vertex.color, vertex.uv)
                    }
                    vertexBuffer.drawMode = primitive.primitiveMode.toGlMode()
                    vertexBuffer.setVertexes(vertices, CooVertexFormat.POINT_COLOR_FORMAT)
                    setFloat("intensity", primitive.pipe.floatParam("intensity") ?: 1f)
                    vertexBuffer.draw()
                }
            }
        }
    }

    fun release() {
        buffer?.release()
        buffer = null
        program?.release()
        program = null
    }

    private fun program(): CooShaderProgram {
        val current = program
        if (current != null) {
            if (current.program == 0) {
                current.init()
            }
            return current
        }
        return SimpleShaderProgram(
            vertexShader = IdentifierShader(shaderId("core/vertex/render_entity_model.vsh"), GlShaderType.VERTEX),
            fragmentShader = IdentifierShader(shaderId("core/fragment/render_entity_model.fsh"), GlShaderType.FRAGMENT)
        )
            .also { created ->
                created.init()
                program = created
            }
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
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glDepthMask(false)
        glDisable(GL_CULL_FACE)
        glLineWidth(1.0f)
        try {
            block()
        } finally {
            glLineWidth(lineWidth)
            glDepthMask(depthMask)
            glDepthFunc(depthFunc)
            glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            if (blendEnabled) {
                glEnable(GL_BLEND)
            } else {
                glDisable(GL_BLEND)
            }
            if (depthEnabled) {
                glEnable(GL_DEPTH_TEST)
            } else {
                glDisable(GL_DEPTH_TEST)
            }
            if (cullEnabled) {
                glEnable(GL_CULL_FACE)
            } else {
                glDisable(GL_CULL_FACE)
            }
        }
    }

    private fun RenderEntityModelPipe.floatParam(name: String): Float? {
        return when (val value = params[name]) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            else -> null
        }
    }

    private fun RenderEntityModelPrimitiveMode.toGlMode(): Int {
        return when (this) {
            RenderEntityModelPrimitiveMode.LINES -> GL_LINES
            RenderEntityModelPrimitiveMode.TRIANGLES -> GL_TRIANGLES
        }
    }

    private fun shaderId(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
