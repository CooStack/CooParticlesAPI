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
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.world.level.block.state.BlockState

object NeoRenderTypesProvider : CooRenderTypesProvider {
    private val cache = LinkedHashMap<CooRenderTypeDescriptor, RenderType>()
    private val entityCutoutEmissiveCache = LinkedHashMap<EntityCutoutEmissiveKey, RenderType>()
    private val terrainCache = LinkedHashMap<Pair<CooRenderPipeline<BlockState>, RenderType>, RenderType>()
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
        return entityCutoutEmissive(texture, brightness, 1F, true)
    }

    override fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float, alpha: Float): RenderType {
        return entityCutoutEmissive(texture, brightness, alpha, alpha < 1F)
    }

    private fun entityCutoutEmissive(
        texture: ResourceLocation,
        brightness: Float,
        alpha: Float,
        translucent: Boolean
    ): RenderType {
        val resolvedBrightness = brightness.coerceAtLeast(0F)
        val resolvedAlpha = alpha.coerceIn(0F, 1F)
        val renderType = entityCutoutEmissiveCache.getOrPut(
            EntityCutoutEmissiveKey(texture, resolvedBrightness, resolvedAlpha, translucent)
        ) {
            val renderTypeName = "coo_entity_cutout_emissive_${resolvedBrightness}_${resolvedAlpha}_$translucent"
            RenderTypeIrisSupposerRegistry.register(renderTypeName, "coo_entity_cutout_emissive")
            val state = RenderType.CompositeState.builder()
                .setShaderState(
                    RenderStateShard.ShaderStateShard {
                        MCShaders.ENTITY_CUTOUT_EMISSIVE.also { shader ->
                            shader.getUniform("Brightness")?.set(resolvedBrightness)
                            shader.getUniform("Alpha")?.set(resolvedAlpha)
                            IrisCompat.markUnskippable(shader)
                        }
                    }
                )
                .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(
                    if (translucent) RenderStateShard.TRANSLUCENT_TRANSPARENCY else RenderStateShard.NO_TRANSPARENCY
                )
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(
                    if (translucent) RenderStateShard.COLOR_WRITE else RenderStateShard.COLOR_DEPTH_WRITE
                )
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)

            RenderType.create(
                renderTypeName,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                translucent,
                state
            )
        }
        return IrisCompat.wrapEntityRenderType(renderType)
    }

    override fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        val renderType = cache.getOrPut(descriptor) {
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
        return IrisCompat.wrapEntityRenderType(renderType)
    }

    override fun terrain(pipeline: CooRenderPipeline<BlockState>, baseLayer: RenderType): RenderType {
        return synchronized(terrainCache) { terrainCache.getOrPut(pipeline to baseLayer) {
            val sorted = baseLayer.sortOnUpload()
            val mipmap = baseLayer === RenderType.solid() ||
                baseLayer === RenderType.cutoutMipped() || baseLayer === RenderType.tripwire()
            RenderType.create(
                "coo_terrain_overlay_${pipeline.id.namespace}_${pipeline.id.path.replace('/', '_')}",
                CooTerrainVertexFormats.BLOCK_EFFECT,
                VertexFormat.Mode.QUADS,
                RenderType.BIG_BUFFER_SIZE,
                baseLayer.affectsCrumbling(),
                sorted,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.ShaderStateShard {
                        CooTerrainPipelineManager.shaderFor(pipeline, baseLayer)
                            ?.also(IrisCompat::markUnskippable)
                    })
                    .setTextureState(RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, mipmap))
                    .setTransparencyState(
                        if (sorted) RenderStateShard.TRANSLUCENT_TRANSPARENCY else RenderStateShard.NO_TRANSPARENCY
                    )
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setWriteMaskState(
                        if (sorted) RenderStateShard.COLOR_WRITE else RenderStateShard.COLOR_DEPTH_WRITE
                    )
                    .setOutputState(
                        when (baseLayer) {
                            RenderType.translucent() -> RenderStateShard.TRANSLUCENT_TARGET
                            RenderType.tripwire() -> RenderStateShard.WEATHER_TARGET
                            else -> RenderStateShard.MAIN_TARGET
                        }
                    )
                    .createCompositeState(true)
            )
        } }
    }

    override fun glow(): RenderType = glow

    private data class EntityCutoutEmissiveKey(
        val texture: ResourceLocation,
        val brightness: Float,
        val alpha: Float,
        val translucent: Boolean
    )
}
