package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType

object NeoRenderTypesProvider : CooRenderTypesProvider {
    val glow: RenderType = RenderType.create(
        "coo_glow",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        256,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(false)
    )
    override fun glow(): RenderType = glow
}