package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Vector3f

/**
 * 描述一个需要在帧尾做 blur + composite 的稳定辉光体。
 *
 * 这些字段不会直接驱动实体本体几何，而是交给 persistent bloom shader：
 * - `position/color/radius/intensity` 定义基础光源
 * - `softness/softOcclusionFloor` 控制边缘与遮挡过渡
 * - `haloRadiusScale/brightnessNormalization/haloOpacity` 控制 halo 外观
 * - `blurSigma/blurRange` 控制最终模糊半径
 */
data class PersistentBloom(
    /** 世界空间中的发光中心。 */
    val position: Vector3f,
    /** bloom 主颜色。 */
    val color: Vector3f,
    /** bloom 基础半径。 */
    val radius: Float,
    /** bloom 基础强度。 */
    val intensity: Float,
    /** 边缘软化程度。 */
    val softness: Float = 0.58f,
    /** 软遮挡最低值，用于减轻硬切边。 */
    val softOcclusionFloor: Float = 0.0f,
    /** 外层 halo 半径相对主体半径的放大倍数。 */
    val haloRadiusScale: Float = 3.0f,
    /** 亮度归一化因子。 */
    val brightnessNormalization: Float = 1.0f,
    /** halo 透明度。 */
    val haloOpacity: Float = 1.0f,
    /** 高斯模糊 sigma。 */
    val blurSigma: Float = 10.0f,
    /** 高斯模糊采样范围。 */
    val blurRange: Float = 6.0f
)
