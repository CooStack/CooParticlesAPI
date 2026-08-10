package cn.coostack.cooparticlesapi.renderer.terrain

import org.joml.Matrix4f

/**
 * 等待 Iris 地形合成完成后恢复调用的 vanilla 帧结束参数。
 *
 * @property tickDelta 本帧渲染使用的部分 tick
 * @property viewMatrix 相机视图矩阵
 * @property projectionMatrix 投影矩阵
 */
internal data class DeferredFrameFinish(
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projectionMatrix: Matrix4f
)
