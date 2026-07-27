package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import org.lwjgl.opengl.GL33.*

/**
 * GPU 粒子渲染层 — 与 [TextureSheetsEnum] / ParticleRenderType 的混合语义一一对应.
 *
 * 同一层的所有系统在一帧内按 [drawOrder] 排序后各自一次 instanced draw.
 * 渲染层只描述混合与深度状态，主纹理由系统的 [CParticleSystem.textureBindingKey] 决定。
 */
enum class CParticleRenderLayer(
    /** 绘制顺序: 越小越先画 (不透明最先, 加法混合最后) */
    val drawOrder: Int,
    val blend: Boolean,
    val blendSrc: Int,
    val blendDst: Int,
    val depthWrite: Boolean,
) {
    /** 对应 PARTICLE_SHEET_OPAQUE / PARTICLE_SHEET_LIT: 无混合, 写深度 */
    OPAQUE(0, false, GL_ONE, GL_ZERO, true),

    /** 对应 PARTICLE_SHEET_TRANSLUCENT: SRC_ALPHA / ONE_MINUS_SRC_ALPHA, 写深度 */
    TRANSLUCENT(1, true, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, true),

    /** 对应 CooParticleTextureSheet.ADDITION_BLEND: ONE/ONE, 写深度 */
    ADDITION_BLEND(3, true, GL_ONE, GL_ONE, true),

    /** 对应 CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT: SRC_ALPHA/ONE, 写深度 */
    ADDITION_BLEND_TRANSLUCENT(2, true, GL_SRC_ALPHA, GL_ONE, true),

    /** 对应 ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE: SRC_ALPHA/ONE, 不写深度 (大量发光粒子推荐) */
    ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE(4, true, GL_SRC_ALPHA, GL_ONE, false);

    val requiresDeferredDepthWrite: Boolean
        get() = blend && blendDst == GL_ONE

    /**
     * 应用本层完整的混合与深度写入状态。
     *
     * Example: `ADDITION_BLEND_TRANSLUCENT.applyState()` 会明确选择加法 equation。
     * Forbidden: 调用方不能依赖进入此方法前残留的 blend equation，并且必须负责恢复状态。
     */
    fun applyState() {
        if (blend) {
            glEnable(GL_BLEND)
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD)
            glBlendFuncSeparate(blendSrc, blendDst, blendSrc, blendDst)
        } else {
            glDisable(GL_BLEND)
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
            "ADDITION_BLEND" -> ADDITION_BLEND
            "ADDITION_BLEND_TRANSLUCENT" -> ADDITION_BLEND_TRANSLUCENT
            "ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE" -> ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE
            else -> TRANSLUCENT
        }

        @JvmStatic
        fun fromSheet(sheet: TextureSheetsEnum): CParticleRenderLayer = fromSheetName(sheet.name)
    }
}
