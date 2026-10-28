package cn.coostack.cooparticlesapi.renderer.state

import cn.coostack.cooparticlesapi.cparticle.CParticleIndexedBlendState
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.backend.opengl.GlStateManager
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
import org.lwjgl.opengl.GL33.GL_MAX_DRAW_BUFFERS
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_FACTOR
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL33.GL_POLYGON_OFFSET_UNITS
import org.lwjgl.opengl.GL33.GL_SCISSOR_BOX
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.glBlendEquationSeparate
import org.lwjgl.opengl.GL33.glBlendFuncSeparate
import org.lwjgl.opengl.GL33.glColorMaski
import org.lwjgl.opengl.GL33.glDepthFunc
import org.lwjgl.opengl.GL33.glDepthMask
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glDisablei
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glEnablei
import org.lwjgl.opengl.GL33.glGetBoolean
import org.lwjgl.opengl.GL33.glGetFloat
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetIntegeri_v
import org.lwjgl.opengl.GL33.glGetIntegerv
import org.lwjgl.opengl.GL33.glIsEnabled
import org.lwjgl.opengl.GL33.glIsEnabledi
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
    /** 兼容旧快照接口；26.3 没有独立 shader 线宽，此值与原生线宽相同。 */
    val shaderLineWidth: Float,
    val lineWidth: Float,
    /** 每个 draw buffer 的混合开关，避免覆盖 Iris 的辅助 attachment。 */
    internal val attachmentBlendEnabled: BooleanArray,
    /** 每个 draw buffer 的 RGBA 位掩码，位值与 RenderPearl 的写入掩码一致。 */
    internal val attachmentColorMasks: IntArray,
    /** 支持独立混合时，各 draw buffer 的源/目标 RGB、源/目标 alpha、RGB/alpha 方程。 */
    internal val attachmentBlendFunctions: List<IntArray>,
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

/**
 * 不依赖 GL 的后进先出状态作用域，负责异常恢复和手动快照的平衡检查。
 *
 * @property captureState 捕获当前状态的回调
 * @property restoreState 恢复指定状态的回调
 */
internal class GLSLStateStack<S>(
    private val captureState: () -> S,
    private val restoreState: (S) -> Unit
) {
    /**
     * @property state 此栈帧保存的状态
     * @property scoped 是否属于不能由手动 reset 关闭的作用域
     */
    private data class Frame<S>(val state: S, val scoped: Boolean)

    /** 尚未恢复的快照，尾部为当前最内层作用域。 */
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

/** 在 OpenGL 3.3 基线上读取状态，并同时恢复 RenderPearl 缓存与实际 GL 状态。 */
private object OpenGlStateBackend {
    /** 独立 attachment 混合函数仅在驱动明确提供对应能力时读取。 */
    fun capture(): GLSLState {
        OpenGlRenderTargetBridge.requireOpenGlBackend()
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
            shaderLineWidth = glGetFloat(GL_LINE_WIDTH),
            lineWidth = glGetFloat(GL_LINE_WIDTH),
            attachmentBlendEnabled = BooleanArray(glGetInteger(GL_MAX_DRAW_BUFFERS)) { glIsEnabledi(GL_BLEND, it) },
            attachmentColorMasks = IntArray(glGetInteger(GL_MAX_DRAW_BUFFERS)) { index ->
                val mask = IntArray(4)
                glGetIntegeri_v(GL_COLOR_WRITEMASK, index, mask)
                colorMaskBits(mask)
            },
            attachmentBlendFunctions = if (CParticleIndexedBlendState.isAvailable()) {
                List(glGetInteger(GL_MAX_DRAW_BUFFERS)) { index ->
                    intArrayOf(
                        indexedInteger(GL_BLEND_SRC_RGB, index),
                        indexedInteger(GL_BLEND_DST_RGB, index),
                        indexedInteger(GL_BLEND_SRC_ALPHA, index),
                        indexedInteger(GL_BLEND_DST_ALPHA, index),
                        indexedInteger(GL_BLEND_EQUATION_RGB, index),
                        indexedInteger(GL_BLEND_EQUATION_ALPHA, index),
                    )
                }
            } else emptyList(),
        )
    }

    /** 先同步缓存，再强制恢复实际状态，兼容 Iris 绕过缓存修改 GL 的场景。 */
    fun restore(state: GLSLState) {
        RenderSystem.assertOnRenderThread()

        GlStateManager._blendFuncSeparate(
            state.blendSrcRgb,
            state.blendDstRgb,
            state.blendSrcAlpha,
            state.blendDstAlpha
        )
        GlStateManager._blendEquationSeparate(state.blendEquationRgb, state.blendEquationAlpha)
        if (state.depthTestEnabled) GlStateManager._enableDepthTest() else GlStateManager._disableDepthTest()
        GlStateManager._depthMask(state.depthMask)
        GlStateManager._depthFunc(state.depthFunc)
        if (state.cullEnabled) GlStateManager._enableCull() else GlStateManager._disableCull()
        GlStateManager._polygonOffset(state.polygonOffsetFactor, state.polygonOffsetUnits)
        if (state.polygonOffsetEnabled) GlStateManager._enablePolygonOffset() else GlStateManager._disablePolygonOffset()
        if (state.scissorEnabled) {
            GlStateManager._scissorBox(
                state.scissorX,
                state.scissorY,
                state.scissorWidth,
                state.scissorHeight
            )
            GlStateManager._enableScissorTest()
        } else {
            GlStateManager._disableScissorTest()
        }

        glBlendFuncSeparate(state.blendSrcRgb, state.blendDstRgb, state.blendSrcAlpha, state.blendDstAlpha)
        glBlendEquationSeparate(state.blendEquationRgb, state.blendEquationAlpha)
        setCapability(GL_DEPTH_TEST, state.depthTestEnabled)
        glDepthMask(state.depthMask)
        glDepthFunc(state.depthFunc)
        setCapability(GL_CULL_FACE, state.cullEnabled)
        glPolygonOffset(state.polygonOffsetFactor, state.polygonOffsetUnits)
        setCapability(GL_POLYGON_OFFSET_FILL, state.polygonOffsetEnabled)
        glScissor(state.scissorX, state.scissorY, state.scissorWidth, state.scissorHeight)
        setCapability(GL_SCISSOR_TEST, state.scissorEnabled)
        glLineWidth(state.lineWidth)
        state.attachmentBlendEnabled.forEachIndexed { index, enabled ->
            if (index < ColorTargetState.MAX_COLOR_TARGETS) {
                if (enabled) GlStateManager._enableBlend(index) else GlStateManager._disableBlend(index)
            }
            if (enabled) glEnablei(GL_BLEND, index) else glDisablei(GL_BLEND, index)
        }
        state.attachmentColorMasks.forEachIndexed { index, mask ->
            if (index < ColorTargetState.MAX_COLOR_TARGETS) {
                GlStateManager._colorMask(index, mask)
            }
            glColorMaski(
                index,
                mask and ColorTargetState.WRITE_RED != 0,
                mask and ColorTargetState.WRITE_GREEN != 0,
                mask and ColorTargetState.WRITE_BLUE != 0,
                mask and ColorTargetState.WRITE_ALPHA != 0,
            )
        }
        state.attachmentBlendFunctions.forEachIndexed { index, function ->
            CParticleIndexedBlendState.setFactors(index, function[0], function[1], function[2], function[3])
            CParticleIndexedBlendState.setEquation(index, function[4], function[5])
        }
    }

    /** 将原版定义的 RGBA 写入位组合为 RenderPearl 接受的掩码。 */
    private fun colorMaskBits(mask: IntArray): Int =
        (if (mask[0] != 0) ColorTargetState.WRITE_RED else 0) or
            (if (mask[1] != 0) ColorTargetState.WRITE_GREEN else 0) or
            (if (mask[2] != 0) ColorTargetState.WRITE_BLUE else 0) or
            (if (mask[3] != 0) ColorTargetState.WRITE_ALPHA else 0)

    /** 读取一个具备独立混合能力的 attachment 状态。 */
    private fun indexedInteger(parameter: Int, index: Int): Int {
        val result = IntArray(1)
        glGetIntegeri_v(parameter, index, result)
        return result[0]
    }

    private fun setCapability(capability: Int, enabled: Boolean) {
        if (enabled) glEnable(capability) else glDisable(capability)
    }
}
