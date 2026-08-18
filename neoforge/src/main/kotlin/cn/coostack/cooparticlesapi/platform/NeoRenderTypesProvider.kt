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
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingBatchKey
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainRenderStateShard
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.world.level.block.state.BlockState

/**
 * 在 NeoForge 客户端创建、缓存并适配 Iris 的 Coo RenderType。
 *
 * 通用描述按值缓存，terrain layer 按批次键和原版基础层缓存；资源重载时由 [clearTerrainCache]
 * 只清除依赖动态 shader 状态的地形缓存。
 */
object NeoRenderTypesProvider : CooRenderTypesProvider {
    private val cache = LinkedHashMap<CooRenderTypeDescriptor, RenderType>()
    private val entityCutoutEmissiveCache = LinkedHashMap<EntityCutoutEmissiveKey, RenderType>()
    private val terrainCache = LinkedHashMap<Pair<Any, RenderType>, RenderType>()
    private val glowId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "glow")

    private val glowDescriptor = CooRenderTypeDescriptor.builder("coo_glow")
        .bufferSize(256)
        .shaderPreset(CooRenderTypeShaderPreset.COO_GLOW)
        .transparencyMode(CooRenderTransparencyMode.ADDITIVE)
        .cullMode(CooRenderCullMode.DISABLED)
        .lightmapMode(CooRenderLightmapMode.DISABLED)
        .depthTestMode(CooRenderDepthTestMode.LEQUAL)
        .build()

    /** 默认加法混合发光 RenderType，优先使用资源注册表中的同名描述。 */
    val glow: RenderType
        get() = named(glowId) ?: create(glowDescriptor)

    /**
     * 创建或复用不透明的 NeoForge 实体裁剪发光 RenderType。
     *
     * @param texture 实体纹理资源
     * @param brightness 发光亮度倍率，负值按 `0` 处理
     * @return 已经过 Iris 实体兼容包装的 RenderType
     */
    override fun entityCutoutEmissive(texture: ResourceLocation, brightness: Float): RenderType {
        return entityCutoutEmissive(texture, brightness, 1F, true)
    }

    /**
     * 创建或复用带透明度的 NeoForge 实体裁剪发光 RenderType。
     *
     * @param texture 实体纹理资源
     * @param brightness 发光亮度倍率，负值按 `0` 处理
     * @param alpha 透明度，限制到 `0.0F..1.0F`
     * @return 已经过 Iris 实体兼容包装的 RenderType
     */
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

    /**
     * 把通用描述转换为 NeoForge RenderType 并按描述复用实例。
     *
     * @param descriptor 完整 RenderType 描述
     * @return 已经过 Iris 实体兼容包装的 RenderType
     */
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

    /**
     * 创建或复用 NeoForge 地形覆盖 RenderType。
     *
     * @param pipeline 提供地形 shader 和渲染配置的 Pipeline
     * @param baseLayer 原版基础 terrain layer
     * @param batchKey 标识可共享同一 RenderType 的地形批次
     * @return 使用扩展地形顶点格式的覆盖 RenderType
     */
    override fun terrain(
        pipeline: CooRenderPipeline<BlockState>,
        baseLayer: RenderType,
        batchKey: Any
    ): RenderType {
        return synchronized(terrainCache) { terrainCache.getOrPut(batchKey to baseLayer) {
            val sorted = baseLayer.sortOnUpload()
            val mipmap = baseLayer === RenderType.solid() ||
                baseLayer === RenderType.cutoutMipped() || baseLayer === RenderType.tripwire()
            lateinit var renderType: RenderType
            renderType = RenderType.create(
                "coo_terrain_overlay_${pipeline.id.namespace}_${pipeline.id.path.replace('/', '_')}",
                CooTerrainVertexFormats.BLOCK_EFFECT,
                VertexFormat.Mode.QUADS,
                RenderType.BIG_BUFFER_SIZE,
                baseLayer.affectsCrumbling(),
                sorted,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.ShaderStateShard {
                        CooTerrainPipelineManager.shaderFor(renderType, baseLayer)
                            ?.also(IrisCompat::markUnskippable)
                    })
                    .setTextureState(RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, mipmap))
                    .setTransparencyState(
                        when (batchKey) {
                            is CooTerrainMappingBatchKey -> when (batchKey.composition) {
                                CooTerrainEffectComposition.REPLACE -> RenderStateShard.NO_TRANSPARENCY
                                CooTerrainEffectComposition.ALPHA_OVER -> RenderStateShard.TRANSLUCENT_TRANSPARENCY
                                CooTerrainEffectComposition.ADDITIVE -> RenderStateShard.ADDITIVE_TRANSPARENCY
                            }
                            else -> if (sorted) RenderStateShard.TRANSLUCENT_TRANSPARENCY else RenderStateShard.NO_TRANSPARENCY
                        }
                    )
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setLayeringState(CooTerrainRenderStateShard.terrainLayering())
                    .setWriteMaskState(CooTerrainRenderStateShard.terrainWriteMask(baseLayer))
                    .setOutputState(CooTerrainRenderStateShard.terrainOutput(baseLayer))
                    .createCompositeState(true)
            )
            renderType
        } }
    }

    /** 清空 NeoForge 地形 RenderType 缓存，使后续区块重建使用新的 shader 资源。 */
    override fun clearTerrainCache() {
        synchronized(terrainCache) {
            terrainCache.clear()
        }
    }

    /** @return 默认加法混合发光 RenderType。 */
    override fun glow(): RenderType = glow

    private data class EntityCutoutEmissiveKey(
        val texture: ResourceLocation,
        val brightness: Float,
        val alpha: Float,
        val translucent: Boolean
    )
}
