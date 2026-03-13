package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Vector3f

data class ScreenGlow(
    val position: Vector3f,
    val color: Vector3f,
    val radius: Float,
    val intensity: Float,
    val softness: Float = 0.48f,
    val haloProfile: Float = 0.0f
)
