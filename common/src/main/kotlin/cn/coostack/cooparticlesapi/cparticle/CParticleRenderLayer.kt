package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendEquationSeparateiARB
import org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL40.glBlendEquationSeparatei
import org.lwjgl.opengl.GL40.glBlendFuncSeparatei

/**
 * GPU 粒子渲染层 — 与 [TextureSheetsEnum] / ParticleRenderType 的混合语义一一对应.
 *
 * 同一层的所有系统在一帧内按 [drawOrder] 排序后各自一次 instanced draw.
 * 渲染层只描述混合与深度状态；基础纹理和蒙版由 [CParticleSystem] 决定。
 */
enum class CParticleRenderLayer(
    /** 绘制顺序: 越小越先画 (不透明最先, 加法混合最后) */
    val drawOrder: Int,
    val blend: Boolean,
    val blendSrc: Int,
    val blendDst: Int,
    val blendSrcAlpha: Int,
    val blendDstAlpha: Int,
    val depthWrite: Boolean,
    /**
     * Screen Blend 前是否把 RGB 乘以片元 Alpha。
     *
     * 示例：透明 NOT_HDR 层设为 `true`，保留粒子 Alpha 对亮度的影响。
     * 禁止在普通 Alpha Over 或标准加法层开启，否则会重复计算 Alpha。
     */
    val premultiplyRgbByAlpha: Boolean = false,
) {
    /** 对应 PARTICLE_SHEET_OPAQUE / PARTICLE_SHEET_LIT: 无混合, 写深度 */
    OPAQUE(0, false, GL_ONE, GL_ZERO, GL_ONE, GL_ZERO, true),

    /** 对应 PARTICLE_SHEET_TRANSLUCENT: SRC_ALPHA / ONE_MINUS_SRC_ALPHA, 写深度 */
    TRANSLUCENT(1, true, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO, true),

    /** 对应 PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE: 原版半透明混合, 不写深度 */
    PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE(
        2,
        true,
        GL_SRC_ALPHA,
        GL_ONE_MINUS_SRC_ALPHA,
        GL_ONE,
        GL_ZERO,
        false,
    ),

    /** 对应 ADDITION_BLEND_TRANSLUCENT_NOT_HDR: 预乘 Alpha 的 Screen Blend, 写深度 */
    ADDITION_BLEND_TRANSLUCENT_NOT_HDR(
        3,
        true,
        GL_ONE,
        GL_ONE_MINUS_SRC_COLOR,
        GL_ZERO,
        GL_ONE,
        true,
        true,
    ),

    /** 对应 ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE: 预乘 Alpha 的 Screen Blend, 不写深度 */
    ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE(
        4,
        true,
        GL_ONE,
        GL_ONE_MINUS_SRC_COLOR,
        GL_ZERO,
        GL_ONE,
        false,
        true,
    ),

    /** 对应 ADDITION_BLEND_NOT_HDR: ONE / ONE_MINUS_SRC_COLOR, 写深度 */
    ADDITION_BLEND_NOT_HDR(5, true, GL_ONE, GL_ONE_MINUS_SRC_COLOR, GL_ZERO, GL_ONE, true),

    /** 对应 ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE: ONE / ONE_MINUS_SRC_COLOR, 不写深度 */
    ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE(6, true, GL_ONE, GL_ONE_MINUS_SRC_COLOR, GL_ZERO, GL_ONE, false),

    /** 对应 CooParticleTextureSheet.ADDITION_BLEND: ONE/ONE, 写深度 */
    ADDITION_BLEND(8, true, GL_ONE, GL_ONE, GL_ONE, GL_ONE, true),

    /** 对应 CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT: SRC_ALPHA/ONE, 写深度 */
    ADDITION_BLEND_TRANSLUCENT(7, true, GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE, true),

    /** 对应 ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE: SRC_ALPHA/ONE, 不写深度 (大量发光粒子推荐) */
    ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE(9, true, GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE, false);

    /**
     * 是否在颜色累加完成后单独写入深度。
     *
     * 示例：[ADDITION_BLEND] 返回 `true`，避免实例顺序截断颜色累加。
     * 禁止：[ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE] 必须保持 `false`，不能遮挡后绘制内容。
     */
    val requiresDeferredDepthWrite: Boolean
        get() = blend && depthWrite && (blendDst == GL_ONE || blendDst == GL_ONE_MINUS_SRC_COLOR)

    /**
     * 应用本层完整的混合与深度写入状态。
     *
     * 示例：`ADDITION_BLEND_TRANSLUCENT.applyState()` 会明确选择加法 equation。
     * 禁止：调用方不能依赖进入此方法前残留的 blend equation，并且必须负责恢复状态。
     */
    fun applyState() {
        if (blend) {
            glEnable(GL_BLEND)
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD)
            glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha)
        } else {
            glDisable(GL_BLEND)
        }
        glDepthMask(depthWrite)
    }

    /**
     * 只覆盖 Iris 主颜色附件的混合状态，并保留 shaderpack 辅助附件配置。
     *
     * 示例：BSL `DRAWBUFFERS:03` 下仅修改 draw buffer `0`，不改写 TAA 元数据附件。
     * 禁止对所有 draw buffers 调用全局 blend factor，否则会破坏 shaderpack 的逐附件状态。
     *
     * @param drawBuffer 主颜色输出在当前 framebuffer draw-buffer 列表中的索引
     */
    fun applyIndexedState(drawBuffer: Int = 0) {
        if (CParticleIndexedBlendState.isAvailable()) {
            if (blend) glEnablei(GL_BLEND, drawBuffer) else glDisablei(GL_BLEND, drawBuffer)
            CParticleIndexedBlendState.setEquation(drawBuffer, GL_FUNC_ADD, GL_FUNC_ADD)
            CParticleIndexedBlendState.setFactors(
                drawBuffer, blendSrc, blendDst, blendSrcAlpha, blendDstAlpha,
            )
        } else {
            applyState()
        }
        glDepthMask(depthWrite)
    }

    companion object {
        /**
         * 从 textureSheet 名称 (ParticleRenderType.toString() / [TextureSheetsEnum].name) 映射.
         * 未知名称回退 TRANSLUCENT.
         */
        @JvmStatic
        fun fromSheetName(name: String): CParticleRenderLayer = when (name) {
            "PARTICLE_SHEET_OPAQUE", "PARTICLE_SHEET_LIT", "TERRAIN_SHEET" -> OPAQUE
            "PARTICLE_SHEET_TRANSLUCENT" -> TRANSLUCENT
            "PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE" -> PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE
            "ADDITION_BLEND_NOT_HDR" -> ADDITION_BLEND_NOT_HDR
            "ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE" -> ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE
            "ADDITION_BLEND_TRANSLUCENT_NOT_HDR" -> ADDITION_BLEND_TRANSLUCENT_NOT_HDR
            "ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE" ->
                ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE
            "ADDITION_BLEND" -> ADDITION_BLEND
            "ADDITION_BLEND_TRANSLUCENT" -> ADDITION_BLEND_TRANSLUCENT
            "ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE" -> ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE
            else -> TRANSLUCENT
        }

        @JvmStatic
        fun fromSheet(sheet: TextureSheetsEnum): CParticleRenderLayer = fromSheetName(sheet.name)
    }
}

internal object CParticleIndexedBlendState {
    fun isAvailable(): Boolean {
        val capabilities = GL.getCapabilities()
        return capabilities.GL_ARB_draw_buffers_blend || capabilities.OpenGL40
    }

    fun setEquation(drawBuffer: Int, rgb: Int, alpha: Int) {
        val capabilities = GL.getCapabilities()
        when {
            capabilities.GL_ARB_draw_buffers_blend ->
                glBlendEquationSeparateiARB(drawBuffer, rgb, alpha)
            capabilities.OpenGL40 ->
                glBlendEquationSeparatei(drawBuffer, rgb, alpha)
            else -> glBlendEquationSeparate(rgb, alpha)
        }
    }

    fun setFactors(
        drawBuffer: Int,
        sourceRgb: Int,
        destinationRgb: Int,
        sourceAlpha: Int,
        destinationAlpha: Int,
    ) {
        val capabilities = GL.getCapabilities()
        when {
            capabilities.GL_ARB_draw_buffers_blend -> glBlendFuncSeparateiARB(
                drawBuffer, sourceRgb, destinationRgb, sourceAlpha, destinationAlpha,
            )
            capabilities.OpenGL40 -> glBlendFuncSeparatei(
                drawBuffer, sourceRgb, destinationRgb, sourceAlpha, destinationAlpha,
            )
            else -> glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
        }
    }
}
