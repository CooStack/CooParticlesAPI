package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import org.joml.Matrix4f
import org.joml.Matrix4fStack

data class LocalRenderInput<T : RenderEntity>(
    val instance: RenderEntityInstance<T>,
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val modelMatrix: Matrix4fStack,
    val renderState: RenderStateGuard.MutableRenderState
)
