package cn.coostack.cooparticlesapi.renderer.backend

import org.joml.Matrix4f

data class RenderFrameContext(
    val tickDelta: Float,
    val viewMatrix: Matrix4f,
    val projMatrix: Matrix4f,
    val backend: RenderBackend,
    val sceneColorTextureId: Int? = null,
    val sceneDepthTextureId: Int? = null
)
