package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.iris.RenderTypeIrisSupposerRegistry
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.display.CooRenderTypeDescriptor
import cn.coostack.cooparticlesapi.display.CooRenderCullMode
import cn.coostack.cooparticlesapi.display.CooRenderDepthTestMode
import cn.coostack.cooparticlesapi.display.CooRenderLightmapMode
import cn.coostack.cooparticlesapi.display.CooRenderTransparencyMode
import cn.coostack.cooparticlesapi.display.CooRenderTypeShaderPreset
import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import cn.coostack.cooparticlesapi.display.CooShaderStateResolver
import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object NeoRenderTypesProvider : CooRenderTypesProvider {
    private val cache = LinkedHashMap<CooRenderTypeDescriptor, RenderType>()
    private val entityCutoutEmissiveCache = LinkedHashMap<Pair<ResourceLocation, Float>, RenderType>()
    private val glowId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "glow")

    private val glowDescriptor = CooRenderTypeDescriptor.builder("coo_glow")
        .bufferSize(256)
        .shaderPreset(CooRenderTypeShaderPreset.COO_GLOW)
        .transparencyMode(CooRenderTransparencyMode.ADDITIVE)
        .cullMode(CooRenderCullMode.DISABLED)
        .lightmapMode(CooRenderLightmapMode.DISABLED)
        .depthTestMode(CooRenderDepthTestMode.LEQUAL)
        .build()

    val glow: RenderType
        get() = named(glowId) ?: create(glowDescriptor)

    override fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float): RenderType {
        val resolvedBrightness = brightness.coerceAtLeast(0f)
        val renderType = entityCutoutEmissiveCache.getOrPut(texture to resolvedBrightness) {
            val renderTypeName = "coo_entity_cutout_emissive_$resolvedBrightness"
            RenderTypeIrisSupposerRegistry.register(renderTypeName, "coo_entity_cutout_emissive")
            val state = RenderType.CompositeState.builder()
                .setShaderState(
                    RenderStateShard.ShaderStateShard {
                        MCShaders.ENTITY_CUTOUT_EMISSIVE.also { shader ->
                            shader.getUniform("Brightness")?.set(resolvedBrightness)
                            IrisCompat.markUnskippable(shader)
                        }
                    }
                )
                .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)

            RenderType.create(
                renderTypeName,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                false,
                state
            )
        }
        return IrisCompat.wrapEntityRenderType(renderType)
    }

    override fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        return cache.getOrPut(descriptor) {
            RenderTypeIrisSupposerRegistry.register(descriptor.name, null)
            RenderType.create(
                descriptor.name,
                descriptor.vertexFormat,
                descriptor.mode,
                descriptor.bufferSize,
                descriptor.affectsCrumbling,
                descriptor.sortOnUpload,
                RenderType.CompositeState.builder()
                    .setShaderState(
                        CooShaderStateResolver.customShaderSupplier(descriptor)
                            ?.let { RenderStateShard.ShaderStateShard(it) }
                            ?: RenderStateShard.POSITION_COLOR_SHADER
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
