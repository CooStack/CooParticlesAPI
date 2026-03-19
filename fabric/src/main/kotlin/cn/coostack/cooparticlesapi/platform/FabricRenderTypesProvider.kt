package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.display.CooRenderTypeDescriptor
import cn.coostack.cooparticlesapi.display.CooRenderCullMode
import cn.coostack.cooparticlesapi.display.CooRenderDepthTestMode
import cn.coostack.cooparticlesapi.display.CooRenderLightmapMode
import cn.coostack.cooparticlesapi.display.CooRenderTransparencyMode
import cn.coostack.cooparticlesapi.display.CooRenderTypeShaderPreset
import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object FabricRenderTypesProvider : CooRenderTypesProvider {
    private val cache = LinkedHashMap<CooRenderTypeDescriptor, RenderType>()
    private val glowId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "glow")

    private val glowDescriptor = CooRenderTypeDescriptor.builder("coo_glow")
        .bufferSize(512)
        .shaderPreset(CooRenderTypeShaderPreset.POSITION_COLOR)
        .transparencyMode(CooRenderTransparencyMode.ADDITIVE)
        .cullMode(CooRenderCullMode.DISABLED)
        .lightmapMode(CooRenderLightmapMode.DISABLED)
        .depthTestMode(CooRenderDepthTestMode.LEQUAL)
        .build()

    val glow: RenderType
        get() = named(glowId) ?: create(glowDescriptor)

    override fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        return cache.getOrPut(descriptor) {
            RenderType.create(
                descriptor.name,
                descriptor.vertexFormat,
                descriptor.mode,
                descriptor.bufferSize,
                descriptor.affectsCrumbling,
                descriptor.sortOnUpload,
                RenderType.CompositeState.builder()
                    .setShaderState(
                        when (descriptor.shaderPreset) {
                            CooRenderTypeShaderPreset.POSITION_COLOR -> RenderStateShard.POSITION_COLOR_SHADER
                        }
                    )
                    .setTransparencyState(
                        when (descriptor.transparencyMode) {
                            CooRenderTransparencyMode.NONE -> RenderStateShard.NO_TRANSPARENCY
                            CooRenderTransparencyMode.ADDITIVE -> RenderStateShard.ADDITIVE_TRANSPARENCY
                        }
                    )
                    .setCullState(
                        when (descriptor.cullMode) {
                            CooRenderCullMode.ENABLED -> RenderStateShard.CULL
                            CooRenderCullMode.DISABLED -> RenderStateShard.NO_CULL
                        }
                    )
                    .setLightmapState(
                        when (descriptor.lightmapMode) {
                            CooRenderLightmapMode.ENABLED -> RenderStateShard.LIGHTMAP
                            CooRenderLightmapMode.DISABLED -> RenderStateShard.NO_LIGHTMAP
                        }
                    )
                    .setDepthTestState(
                        when (descriptor.depthTestMode) {
                            CooRenderDepthTestMode.LEQUAL -> RenderStateShard.LEQUAL_DEPTH_TEST
                        }
                    )
                    .createCompositeState(false)
            )
        }
    }

    override fun glow(): RenderType = glow
}
