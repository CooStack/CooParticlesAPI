package cn.coostack.cooparticlesapi.renderer.glow

interface ScreenGlowProvider {
    fun collectScreenGlows(tickDelta: Float, output: MutableList<ScreenGlow>)
}
