package cn.coostack.cooparticlesapi.display

import net.minecraft.client.renderer.RenderType

interface CooRenderTypesProvider {
    fun glow(): RenderType
}