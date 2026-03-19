package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/**
 * world pass 本地绘制阶段传给 renderer 的输入上下文。
 *
 * @property instance 当前正在渲染的 RenderEntityInstance
 * @property tickDelta 当前帧的部分 tick 插值值
 * @property viewMatrix 相机 view 矩阵
 * @property projMatrix 当前帧 projection 矩阵
 * @property modelMatrix 已经进入实体局部空间的模型矩阵栈
 * @property renderState 本次绘制可写的渲染状态句柄
 */
data class LocalRenderInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4fStack,
    val renderState: RenderStateGuard.MutableRenderState
)
