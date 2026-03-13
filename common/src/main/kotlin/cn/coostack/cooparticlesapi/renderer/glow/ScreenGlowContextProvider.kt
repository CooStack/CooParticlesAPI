package cn.coostack.cooparticlesapi.renderer.glow

interface ScreenGlowContextProvider {
    fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>)
}
