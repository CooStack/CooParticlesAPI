package cn.coostack.cooparticlesapi.renderer.effects

fun interface FrameEffectCollector {
    fun submit(effect: FrameEffectSubmission)
}
