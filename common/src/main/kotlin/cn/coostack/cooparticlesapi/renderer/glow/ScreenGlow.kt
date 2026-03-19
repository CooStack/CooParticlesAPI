package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Vector3f

/**
 * 一次屏幕空间 glow 提交的基础参数。
 */
data class ScreenGlow(
    /** 世界空间中的光源中心。 */
    val position: Vector3f,
    /** glow 主颜色。 */
    val color: Vector3f,
    /** glow 半径。 */
    val radius: Float,
    /** glow 强度。 */
    val intensity: Float,
    /** 边缘柔和度。 */
    val softness: Float = 0.48f,
    /** 外圈 halo 的额外轮廓权重。 */
    val haloProfile: Float = 0.0f
)
