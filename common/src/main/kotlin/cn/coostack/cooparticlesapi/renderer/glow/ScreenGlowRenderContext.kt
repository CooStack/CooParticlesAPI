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
    /** 当前帧部分 tick 插值。 */
    val tickDelta: Float,
    /** 相机在世界空间中的位置。 */
    val cameraWorldPos: Vector3f,
    /** 当前 view 矩阵。 */
    val viewMatrix: Matrix4f,
    /** 仅旋转部分的 view 矩阵。 */
    val viewRotationMatrix: Matrix3f,
    /** `viewRotationMatrix` 的逆矩阵。 */
    val inverseViewRotationMatrix: Matrix3f,
    /** 当前 projection 矩阵。 */
    val projMatrix: Matrix4f,
    /** 当前屏幕尺寸。 */
    val screenSize: Vector2f
)
