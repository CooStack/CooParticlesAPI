package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.display.CooRenderTypeDescriptor
import cn.coostack.cooparticlesapi.display.CooRenderTypePipelineFactory
import cn.coostack.cooparticlesapi.display.CooRenderCullMode
import cn.coostack.cooparticlesapi.display.CooRenderDepthTestMode
import cn.coostack.cooparticlesapi.display.CooRenderLightmapMode
import cn.coostack.cooparticlesapi.display.CooRenderTransparencyMode
import cn.coostack.cooparticlesapi.display.CooRenderTypeShaderPreset
import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState

/**
 * 在 NeoForge 客户端创建、缓存并适配 Iris 的 Coo RenderType。
 *
 * 通用描述按值缓存，terrain layer 按批次键和原版基础层缓存；资源重载时由 [clearTerrainCache]
 * 只清除依赖动态 shader 状态的地形缓存。
 */
object NeoRenderTypesProvider : CooRenderTypesProvider {
    private val glowId = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "glow")

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
    override fun entityCutoutEmissive(texture: Identifier, brightness: Float): RenderType =
        CooRenderTypePipelineFactory.entityCutoutEmissive(texture, brightness, 1F, true)

    /**
     * 创建或复用带透明度的 NeoForge 实体裁剪发光 RenderType。
     *
     * @param texture 实体纹理资源
     * @param brightness 发光亮度倍率，负值按 `0` 处理
     * @param alpha 透明度，限制到 `0.0F..1.0F`
     * @return 已经过 Iris 实体兼容包装的 RenderType
     */
    override fun entityCutoutEmissive(texture: Identifier, brightness: Float, alpha: Float): RenderType =
        CooRenderTypePipelineFactory.entityCutoutEmissive(texture, brightness, alpha, alpha < 1F)

    /**
     * 把通用描述转换为 NeoForge RenderType 并按描述复用实例。
     *
     * @param descriptor 完整 RenderType 描述
     * @return 已经过 Iris 实体兼容包装的 RenderType
     */
    override fun create(descriptor: CooRenderTypeDescriptor): RenderType =
        CooRenderTypePipelineFactory.create(descriptor)

    /**
     * 创建或复用 NeoForge 自有覆盖网格使用的批次身份，不直接提交到原版 collector。
     *
     * @param pipeline 提供地形 shader 和渲染配置的 Pipeline
     * @param baseLayer 原版基础 terrain layer
     * @param batchKey 标识可共享同一 RenderType 的地形批次
     * @return 描述扩展顶点布局的批次身份，实际程序由地形覆盖所有者绑定
     */
    override fun terrain(
        pipeline: CooRenderPipeline<BlockState>,
        baseLayer: RenderType,
        batchKey: Any
    ): RenderType = CooRenderTypePipelineFactory.terrain(pipeline, baseLayer, batchKey)

    /** 清空 NeoForge 地形 RenderType 缓存，使后续区块重建使用新的 shader 资源。 */
    override fun clearTerrainCache() {
        CooRenderTypePipelineFactory.clearTerrainCache()
    }

    /** @return 默认加法混合发光 RenderType。 */
    override fun glow(): RenderType = glow

}
