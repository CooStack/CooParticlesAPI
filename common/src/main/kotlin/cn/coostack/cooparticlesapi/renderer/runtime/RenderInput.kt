package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineNode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/** 当前实体的一次几何绘制输入。 */
class RenderInput<T : RenderEntity> internal constructor(
    val entity: T,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4fStack,
    val renderState: RenderStateGuard.MutableRenderState,
    internal val pipeline: CooRenderPipeline<T>,
    internal val node: CooPipelineNode,
    internal val phase: RenderPhase,
    internal val output: CooPipelineOutputPort? = null
)

internal enum class RenderPhase {
    WORLD,
    OFFSCREEN
}
