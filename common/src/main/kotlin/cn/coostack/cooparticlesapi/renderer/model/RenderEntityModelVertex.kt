package cn.coostack.cooparticlesapi.renderer.model

import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f

data class RenderEntityModelVertex(
    val position: Vector3f,
    val color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    val uv: Vector2f = Vector2f(0f, 0f),
    val uv1: Vector2i = Vector2i(0, 10),
    val uv2: Vector2i = Vector2i(0xF000F0 and 0xFFFF, 0xF000F0 ushr 16),
    val normal: Vector3f = Vector3f(0f, 1f, 0f)
)
