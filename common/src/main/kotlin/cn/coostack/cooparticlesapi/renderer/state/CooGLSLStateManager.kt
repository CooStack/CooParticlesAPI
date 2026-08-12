package cn.coostack.cooparticlesapi.renderer.state

import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_EQUATION_RGB
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL33.GL_COLOR_WRITEMASK
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL33.GL_LINE_WIDTH
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_FACTOR
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_UNITS
import org.lwjgl.opengl.GL33.GL_SCISSOR_BOX
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.glBlendEquationSeparate
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glColorMask
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetFloat
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glLineWidth
import org.lwjgl.opengl.GL33.glPolygonOffset
import org.lwjgl.opengl.GL33.glScissor

/**
 * 一次可恢复的 OpenGL 光栅状态快照。
 *
 * 快照包含 RenderEntity 绘制会修改的混合、深度、剔除、裁剪、颜色写入、polygon offset
 * 和线宽状态。Shader program、纹理、VAO、framebuffer 与 viewport 仍由对应资源对象管理，
 * 不能用本快照替代它们的绑定生命周期。
 */
class GLSLState internal constructor(
    val blendEnabled: Boolean,
    val blendSrcRgb: Int,
    val blendDstRgb: Int,
    val blendSrcAlpha: Int,
    val blendDstAlpha: Int,
    val blendEquationRgb: Int,
    val blendEquationAlpha: Int,
    val depthTestEnabled: Boolean,
    val depthMask: Boolean,
    val depthFunc: Int,
    val cullEnabled: Boolean,
    val scissorEnabled: Boolean,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
    val colorMaskRed: Boolean,
    val colorMaskGreen: Boolean,
    val colorMaskBlue: Boolean,
    val colorMaskAlpha: Boolean,
    val polygonOffsetEnabled: Boolean,
    val polygonOffsetFactor: Float,
    val polygonOffsetUnits: Float,
    val shaderLineWidth: Float,
    val lineWidth: Float
)

/**
 * 管理 RenderEntity 绘制期间的 OpenGL 光栅状态栈。
 *
 * [createState] 每次都从当前 OpenGL 上下文读取状态并压栈，[resetState] 按后进先出顺序恢复。
 * 两个方法必须成对调用。一般应优先使用 [useState]，它会在异常退出时恢复状态，并检查作用域内
 * 是否遗留了未重置的手动快照。
 */
object CooGLSLStateManager {
    private val states = GLSLStateStack(OpenGlStateBackend::capture, OpenGlStateBackend::restore)

    /** 保存当前状态并压栈。 */
    @JvmStatic
    fun createState(): GLSLState {
        RenderSystem.assertOnRenderThread()
        return states.createState()
    }

    /** 恢复并移除最后一次由 [createState] 保存的状态。 */
    @JvmStatic
    fun resetState(): GLSLState {
        RenderSystem.assertOnRenderThread()
        return states.resetState()
    }

    /**
     * 在可恢复的状态作用域中执行 [block]。
     *
     * 作用域结束时会恢复入口状态。若 [block] 内调用 [createState] 后没有成对调用 [resetState]，
     * 管理器会先清理遗留快照，再抛出状态栈失衡异常。
     */
    @JvmStatic
    fun <T> useState(block: () -> T): T {
        RenderSystem.assertOnRenderThread()
        return states.useState(block)
    }

    /**
     * 检查调用边界外是否遗留了手动快照。
     *
     * 若存在遗留状态，会按后进先出顺序恢复并抛出异常。帧入口调用该方法可防止一次漏掉的
     * [resetState] 把陈旧状态带入后续世界渲染。
     */
    @JvmStatic
    fun assertStateStackEmpty() {
        RenderSystem.assertOnRenderThread()
        states.assertEmpty()
    }
}

internal class GLSLStateStack<S>(
    private val captureState: () -> S,
    private val restoreState: (S) -> Unit
) {
    private data class Frame<S>(val state: S, val scoped: Boolean)

    private val frames = ArrayDeque<Frame<S>>()

    fun createState(): S {
        val state = captureState()
        frames.addLast(Frame(state, scoped = false))
        return state
    }

    fun resetState(): S {
        check(frames.isNotEmpty()) {
            "GLSL state stack is empty; resetState() must match an earlier createState()"
        }
        val frame = frames.last()
        check(!frame.scoped) {
            "resetState() cannot close a managed useState scope; only reset states created inside that scope"
        }
        frames.removeLast()
        restoreState(frame.state)
        return frame.state
    }

    fun <T> useState(block: () -> T): T {
        val entryDepth = frames.size
        frames.addLast(Frame(captureState(), scoped = true))
        var blockFailure: Throwable? = null
        try {
            return block()
        } catch (error: Throwable) {
            blockFailure = error
            throw error
        } finally {
            val closingFailure = closeScope(entryDepth)
            if (closingFailure != null) {
                if (blockFailure == null) {
                    throw closingFailure
                }
                blockFailure.addSuppressed(closingFailure)
            }
        }
    }

    fun assertEmpty() {
        if (frames.isEmpty()) return
        check(frames.none(Frame<S>::scoped)) {
            "GLSL state stack contains an active useState scope at a render boundary"
        }
        val leakedStateCount = frames.size
        var restoreFailure: Throwable? = null
        while (frames.isNotEmpty()) {
            val frame = frames.removeLast()
            try {
                restoreState(frame.state)
            } catch (error: Throwable) {
                if (restoreFailure == null) {
                    restoreFailure = error
                } else {
                    restoreFailure.addSuppressed(error)
                }
            }
        }
        val error = IllegalStateException(
            "GLSL state stack is unbalanced: $leakedStateCount createState() call(s) crossed a render boundary"
        )
        restoreFailure?.let(error::addSuppressed)
        throw error
    }

    private fun closeScope(entryDepth: Int): Throwable? {
        val leakedStateCount = (frames.size - entryDepth - 1).coerceAtLeast(0)
        var failure: Throwable? = if (leakedStateCount > 0) {
            IllegalStateException(
                "GLSL state stack is unbalanced: $leakedStateCount createState() call(s) were not reset"
            )
        } else {
            null
        }

        while (frames.size > entryDepth) {
            val frame = frames.removeLast()
            try {
                restoreState(frame.state)
            } catch (error: Throwable) {
                if (failure == null) {
                    failure = error
                } else {
                    failure.addSuppressed(error)
                }
            }
        }
        return failure
    }
}

private object OpenGlStateBackend {
    fun capture(): GLSLState {
        RenderSystem.assertOnRenderThread()
        val scissorBox = IntArray(4)
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox)
        val colorMask = IntArray(4)
        glGetIntegerv(GL_COLOR_WRITEMASK, colorMask)
        return GLSLState(
            blendEnabled = glIsEnabled(GL_BLEND),
            blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB),
            blendDstRgb = glGetInteger(GL_BLEND_DST_RGB),
            blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA),
            blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA),
            blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB),
            blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA),
            depthTestEnabled = glIsEnabled(GL_DEPTH_TEST),
            depthMask = glGetBoolean(GL_DEPTH_WRITEMASK),
            depthFunc = glGetInteger(GL_DEPTH_FUNC),
            cullEnabled = glIsEnabled(GL_CULL_FACE),
            scissorEnabled = glIsEnabled(GL_SCISSOR_TEST),
            scissorX = scissorBox[0],
            scissorY = scissorBox[1],
            scissorWidth = scissorBox[2],
            scissorHeight = scissorBox[3],
            colorMaskRed = colorMask[0] != 0,
            colorMaskGreen = colorMask[1] != 0,
            colorMaskBlue = colorMask[2] != 0,
            colorMaskAlpha = colorMask[3] != 0,
            polygonOffsetEnabled = glIsEnabled(GL_POLYGON_OFFSET_FILL),
            polygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR),
            polygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS),
            shaderLineWidth = RenderSystem.getShaderLineWidth(),
            lineWidth = glGetFloat(GL_LINE_WIDTH)
        )
    }

    fun restore(state: GLSLState) {
        RenderSystem.assertOnRenderThread()

        if (state.blendEnabled) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        RenderSystem.blendFuncSeparate(
            state.blendSrcRgb,
            state.blendDstRgb,
            state.blendSrcAlpha,
            state.blendDstAlpha
        )
        RenderSystem.blendEquation(state.blendEquationRgb)
        if (state.depthTestEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
        RenderSystem.depthMask(state.depthMask)
        RenderSystem.depthFunc(state.depthFunc)
        if (state.cullEnabled) RenderSystem.enableCull() else RenderSystem.disableCull()
        RenderSystem.colorMask(
            state.colorMaskRed,
            state.colorMaskGreen,
            state.colorMaskBlue,
            state.colorMaskAlpha
        )
        RenderSystem.polygonOffset(state.polygonOffsetFactor, state.polygonOffsetUnits)
        if (state.polygonOffsetEnabled) RenderSystem.enablePolygonOffset() else RenderSystem.disablePolygonOffset()
        if (state.scissorEnabled) {
            RenderSystem.enableScissor(
                state.scissorX,
                state.scissorY,
                state.scissorWidth,
                state.scissorHeight
            )
        } else {
            RenderSystem.disableScissor()
        }
        RenderSystem.lineWidth(state.shaderLineWidth)

        setCapability(GL_BLEND, state.blendEnabled)
        glBlendFuncSeparate(state.blendSrcRgb, state.blendDstRgb, state.blendSrcAlpha, state.blendDstAlpha)
        glBlendEquationSeparate(state.blendEquationRgb, state.blendEquationAlpha)
        setCapability(GL_DEPTH_TEST, state.depthTestEnabled)
        glDepthMask(state.depthMask)
        glDepthFunc(state.depthFunc)
        setCapability(GL_CULL_FACE, state.cullEnabled)
        glColorMask(
            state.colorMaskRed,
            state.colorMaskGreen,
            state.colorMaskBlue,
            state.colorMaskAlpha
        )
        glPolygonOffset(state.polygonOffsetFactor, state.polygonOffsetUnits)
        setCapability(GL_POLYGON_OFFSET_FILL, state.polygonOffsetEnabled)
        glScissor(state.scissorX, state.scissorY, state.scissorWidth, state.scissorHeight)
        setCapability(GL_SCISSOR_TEST, state.scissorEnabled)
        glLineWidth(state.lineWidth)
    }

    private fun setCapability(capability: Int, enabled: Boolean) {
        if (enabled) glEnable(capability) else glDisable(capability)
    }
}
