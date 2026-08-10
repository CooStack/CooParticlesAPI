package cn.coostack.cooparticlesapi.test.options.renderer.world

import org.joml.Vector4f

interface DemoWorldRenderEffectSpec {
    val radius: Float
    val intensity: Float
    val durationTicks: Int
    val displayName: String
    val effectColor: Vector4f
}
