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
    val position: Vector3f,
    val color: Vector3f,
    val radius: Float,
    val intensity: Float,
    val softness: Float = 0.58f,
    val softOcclusionFloor: Float = 0.0f,
    val haloRadiusScale: Float = 3.0f,
    val brightnessNormalization: Float = 1.0f,
    val haloOpacity: Float = 1.0f,
    val blurSigma: Float = 10.0f,
    val blurRange: Float = 6.0f
)
