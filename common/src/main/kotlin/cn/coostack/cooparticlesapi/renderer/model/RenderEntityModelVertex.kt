package cn.coostack.cooparticlesapi.renderer.model

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

data class RenderEntityModelVertex(
    val position: Vector3f,
    val color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    val uv: Vector2f = Vector2f(0f, 0f),
    val normal: Vector3f = Vector3f(0f, 1f, 0f)
)
