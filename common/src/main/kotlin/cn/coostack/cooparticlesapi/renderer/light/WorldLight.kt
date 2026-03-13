package cn.coostack.cooparticlesapi.renderer.light

import org.joml.Vector3f

data class WorldLight(
    val position: Vector3f,
    val color: Vector3f,
    val radius: Float,
    val intensity: Float,
    val normal: Vector3f = Vector3f(0f, 1f, 0f),
    val shape: WorldLightShape = WorldLightShape.POINT,
    val softness: Float = 0.42f
)

enum class WorldLightShape {
    POINT,
    DISK
}
