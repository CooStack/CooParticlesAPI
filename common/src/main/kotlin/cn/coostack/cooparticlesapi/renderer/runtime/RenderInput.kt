package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/**
 * 当前实体的一次几何绘制输入。
 *
 * 该对象把一次节点绘制所需的实体、相机矩阵、模型矩阵和可变渲染状态集中传给
 * renderer。调用方通常只读取这些字段并通过 [modelMatrix] 追加当前节点的绘制数据，
 * 不应跨帧缓存实例。OpenGL 光栅状态由 runtime 在回调外统一保存和恢复。
 *
 * @param entity 当前正在绘制的 RenderEntity；其位置和自定义状态会影响最终几何结果
 * @param tickDelta 当前 tick 内的插值比例，范围通常为 0..1，用于平滑位置和动画
 * @param viewMatrix 相机视图矩阵，将世界坐标变换到相机空间
 * @param projMatrix 投影矩阵，将相机空间坐标变换到裁剪空间
 * @param modelMatrix 当前节点使用的模型矩阵栈；压入和弹出必须成对进行
 * @param renderState 仅为旧版 renderer 保留的内存状态对象，不代表真实 OpenGL 状态
 * @param pipeline 当前实体正在执行的不可变 Pipeline
 * @param node 当前帧正在执行的 Pipeline 节点
 * @param phase 当前节点属于世界绘制还是离屏绘制
 * @param output 当前节点的输出端口；没有显式输出时为 null
 *
 * 示例：在 renderer 回调中读取插值时间：`val renderAge = input.entity.age + input.tickDelta`。
 */
class RenderInput<T : RenderEntity> internal constructor(
    val entity: T,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4fStack,
    @Deprecated("真实 OpenGL 状态已由 CooGLSLStateManager 自动管理")
    val renderState: RenderStateGuard.MutableRenderState,
    internal val pipeline: CooRenderPipeline<T>,
    internal val node: CooPipelineNode,
    internal val phase: RenderPhase,
    internal val output: CooPipelineOutputPort? = null
)

/**
 * 实体节点的绘制阶段。
 *
 * 阶段会影响当前节点可访问的 framebuffer 和矩阵语义：世界阶段直接参与场景几何，
 * 离屏阶段写入后处理目标，不应把离屏结果当作世界深度使用。
 */
internal enum class RenderPhase {
    /** 世界或实体几何绘制阶段，结果写入场景目标。 */
    WORLD,
    /** 离屏节点阶段，结果写入临时或后处理 framebuffer。 */
    OFFSCREEN
}
