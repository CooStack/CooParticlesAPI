package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f

/**
 * glow / bloom 采样阶段统一使用的屏幕空间上下文。
 *
 * 这里放的是“这一帧投影分析所需的公共输入”：
 * - 相机世界坐标
 * - view / projection 相关矩阵
 * - 当前屏幕尺寸
 * - 当前 tickDelta
 *
 * `DistanceAdaptiveGlow`、`ScreenGlowContextProvider`、`PersistentBloomContextProvider`
 * 都依赖这份上下文来判断一个世界物体在当前帧投影到屏幕后到底有多大。
 */
data class ScreenGlowRenderContext(
    val tickDelta: Float,
    val cameraWorldPos: Vector3f,
    val viewMatrix: Matrix4f,
    val viewRotationMatrix: Matrix3f,
    val inverseViewRotationMatrix: Matrix3f,
    val projMatrix: Matrix4f,
    val screenSize: Vector2f
)
