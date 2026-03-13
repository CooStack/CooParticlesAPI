package cn.coostack.cooparticlesapi.renderer.light

interface WorldLightProvider {
    fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>)
}
