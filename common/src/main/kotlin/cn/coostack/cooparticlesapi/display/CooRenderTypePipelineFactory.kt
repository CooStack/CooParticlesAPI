package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.iris.RenderTypeIrisSupposerRegistry
import cn.coostack.cooparticlesapi.compat.iris.CooIrisNativePipelineDraw
import cn.coostack.cooparticlesapi.mixin.ParticleRenderTypeCreateInvoker
import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.pipeline.CooTerrainLayer
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingBatchKey
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainVertexFormats
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.world.level.block.state.BlockState
import java.util.Optional

/**
 * 两个加载器共用的描述符、实体发光和地形批次身份工厂，不持有实际 GPU 程序。
 *
 * 通过原版 RenderSetup 与命令收集器提交，保留 Iris 的实体通道包装。
 * 示例：`create(descriptor)`、`entityCutoutEmissive(texture, 2F, 0.5F, true)`。
 */
object CooRenderTypePipelineFactory {
    /** 描述符值相同的绘制类型在资源周期内共享。 */
    private val types = linkedMapOf<CooRenderTypeDescriptor, RenderType>()
    /** 纹理与常量倍率相同的实体管线共享，避免每帧构造编译变体。 */
    private val emissiveTypes = linkedMapOf<EmissiveKey, RenderType>()
    /** 相同亮度、透明度和混合方式跨纹理共享程序，纹理不应触发重复编译。 */
    private val emissivePipelines = linkedMapOf<Triple<Float, Float, Boolean>, RenderPipeline>()
    /** 地形覆盖所有者使用的批次身份，不是可交给原版 collector 绘制的地形材质。 */
    private val terrainTypes = linkedMapOf<TerrainIdentityKey, RenderType>()
    /** 身份管线独立于实际地形程序，只用于布局与状态描述并随资源周期预编译。 */
    private val terrainPipelines = linkedSetOf<RenderPipeline>()
    /** 当前周期内生成的变体，供后续资源重载预编译。 */
    private val pipelines = linkedSetOf<RenderPipeline>()
    /** 管线只需本机唯一诊断标识，不参与网络协议。 */
    private var nextPipelineId = 0L

    /**
     * 完整复制自定义管线的程序、宏、布局和颜色目标，再应用描述符的绘制状态。
     * 示例：`create(CooRenderTypeDescriptor.builder("marker").build())`。
     * @param descriptor 该次资源周期内保持不变的渲染配置
     * @return 可提交到原版收集器、保留 Iris 实体标记的绘制类型
     */
    fun create(descriptor: CooRenderTypeDescriptor): RenderType {
        val type = types.getOrPut(descriptor) {
            val source = CooShaderStateResolver.pipeline(descriptor)
            val blend = when (descriptor.transparencyMode) {
                CooRenderTransparencyMode.NONE -> null
                CooRenderTransparencyMode.ADDITIVE -> BlendFunction.ADDITIVE
            }
            val builder = copyBuilder(source)
                .withVertexBinding(0, descriptor.vertexFormat)
                .withPrimitiveTopology(descriptor.mode)
                .withCull(descriptor.cullMode == CooRenderCullMode.ENABLED)
                .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            source.colorTargetStates.forEachIndexed { index, state ->
                if (state != null) builder.withColorTargetState(
                    index, ColorTargetState(Optional.ofNullable(blend), state.format(), state.writeMask()),
                )
            }
            val pipeline = builder.build().also(pipelines::add)
            if (descriptor.shaderPreset == CooRenderTypeShaderPreset.POSITION_COLOR) {
                IrisCompat.assignPipeline(pipeline, "BASIC")
            } else {
                CooIrisNativePipelineDraw.register(
                    pipeline, if (descriptor.shaderPreset == CooRenderTypeShaderPreset.COO_GLOW) {
                        "BASIC_COLOR"
                    } else if (descriptor.transparencyMode != CooRenderTransparencyMode.NONE) {
                        "ENTITIES_TRANSLUCENT"
                    } else "ENTITIES_CUTOUT"
                )
            }
            val setup = RenderSetup.builder(pipeline)
                .apply {
                    if (descriptor.lightmapMode == CooRenderLightmapMode.ENABLED) useLightmap()
                    if (descriptor.affectsCrumbling) affectsCrumbling()
                    if (descriptor.sortOnUpload) sortOnUpload()
                }
                .createRenderSetup()
            RenderTypeIrisSupposerRegistry.register(descriptor.name, null)
            ParticleRenderTypeCreateInvoker.createParticleRenderType(descriptor.name, setup)
        }
        return IrisCompat.wrapEntityRenderType(type)
    }

    /**
     * 保留原裁剪、叠加纹理、亮度、透明度与透明时不写深度的实体绘制语义。
     * 倍率为该缓存项的常量，转成程序宏后不会在延迟提交时串用其他实体的 uniform。
     * 示例：`entityCutoutEmissive(texture, 2F, 0.5F, true)`。
     * @param texture 实体纹理
     * @param brightness 非负亮度倍率，必须有限
     * @param alpha 有限透明度，限制到零至一
     * @param translucent 是否透明混合并禁止深度写入
     * @return 原版收集器可提交且保留 Iris 实体包装的绘制类型
     */
    fun entityCutoutEmissive(texture: Identifier, brightness: Float, alpha: Float, translucent: Boolean): RenderType {
        require(brightness.isFinite() && alpha.isFinite()) { "实体亮度和透明度必须有限" }
        val key = EmissiveKey(texture, brightness.coerceAtLeast(0F), alpha.coerceIn(0F, 1F), translucent)
        val type = emissiveTypes.getOrPut(key) {
            val pipeline = emissivePipelines.getOrPut(Triple(key.brightness, key.alpha, key.translucent)) {
                copyBuilder(MCShaders.ENTITY_CUTOUT_EMISSIVE)
                    .withShaderDefine("COO_BRIGHTNESS", key.brightness)
                    .withShaderDefine("COO_ALPHA", key.alpha)
                    .withColorTargetState(if (key.translucent) ColorTargetState(BlendFunction.TRANSLUCENT) else ColorTargetState.DEFAULT)
                    .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, !key.translucent))
                    .build().also { pipeline ->
                        pipelines.add(pipeline)
                        CooIrisNativePipelineDraw.register(
                            pipeline, if (key.translucent) "ENTITIES_EYES_TRANS" else "ENTITIES_EYES"
                        )
                    }
            }
            val name = "coo_entity_cutout_emissive_${key.brightness}_${key.alpha}_${key.translucent}"
            val setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .useOverlay()
                .affectsCrumbling()
                .apply { if (key.translucent) sortOnUpload() }
                .createRenderSetup()
            RenderTypeIrisSupposerRegistry.register(name, "coo_entity_cutout_emissive")
            ParticleRenderTypeCreateInvoker.createParticleRenderType(name, setup)
        }
        return IrisCompat.wrapEntityRenderType(type)
    }

    /**
     * 清除依赖自定义管线供应器的引用，不关闭原版拥有的管线程序。
     * 示例：`clearCaches()`。
     */
    fun clearCaches() {
        types.clear()
        emissiveTypes.clear()
        emissivePipelines.clear()
        pipelines.clear()
        CooIrisNativePipelineDraw.clear()
        clearTerrainCache()
    }

    /**
     * 为自有 section overlay mesh 创建稳定批次身份及准确的扩展顶点布局。
     *
     * 此类型只能交给 CooTerrainPipelineManager 的覆盖绘制所有者，不能直接交给原版 collector。
     * 实际 shader、uniform、附件及深度偏移在覆盖所有者的作用域内绑定；身份片元程序故意不输出，
     * 防止误提交时使用基础程序伪装成自定义地形效果。
     * 示例：`terrain(pipeline, baseLayer, batchKey)`。
     * @param pipeline 拥有实际地形程序和动态参数的框架管线
     * @param baseLayer 原始地形层，保留采样与排序身份
     * @param batchKey 同一几何批次的稳定键；仅更新 uniform 时不改变
     * @return 仅供覆盖所有者查询布局和关联实际程序的批次身份
     */
    fun terrain(pipeline: CooRenderPipeline<BlockState>, baseLayer: RenderType, batchKey: Any): RenderType =
        synchronized(terrainTypes) {
            terrainTypes.getOrPut(TerrainIdentityKey(pipeline, baseLayer, batchKey)) {
                val composition = (batchKey as? CooTerrainMappingBatchKey)?.composition
                    ?: if (baseLayer.sortOnUpload()) CooTerrainEffectComposition.ALPHA_OVER else CooTerrainEffectComposition.REPLACE
                val blend = when (composition) {
                    CooTerrainEffectComposition.REPLACE -> null
                    CooTerrainEffectComposition.ALPHA_OVER -> BlendFunction.TRANSLUCENT
                    CooTerrainEffectComposition.ADDITIVE ->
                        BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)
                }
                val identity = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipeline/terrain_identity/${nextPipelineId++}"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/coo_terrain_identity"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/coo_terrain_identity"))
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                    .withVertexBinding(0, CooTerrainVertexFormats.BLOCK_EFFECT)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(true)
                    .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withColorTargetState(blend?.let(::ColorTargetState) ?: ColorTargetState.DEFAULT)
                    .build().also(terrainPipelines::add)
                val mipmap = baseLayer === CooTerrainPipelineManager.baseLayer(CooTerrainLayer.SOLID) ||
                    baseLayer === CooTerrainPipelineManager.baseLayer(CooTerrainLayer.CUTOUT_MIPPED)
                val setup = RenderSetup.builder(identity)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS) {
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, mipmap)
                    }
                    .useLightmap()
                    .apply {
                        if (baseLayer.sortOnUpload()) sortOnUpload()
                        if (baseLayer.affectsCrumbling()) affectsCrumbling()
                    }
                    .createRenderSetup()
                ParticleRenderTypeCreateInvoker.createParticleRenderType(
                    "coo_terrain_identity_${pipeline.id.namespace}_${pipeline.id.path.replace('/', '_')}", setup,
                )
            }
        }

    /**
     * 清空地形批次身份；现有覆盖网格由地形所有者按其生命周期释放。
     * 示例：`clearTerrainCache()`。
     */
    fun clearTerrainCache() {
        synchronized(terrainTypes) {
            terrainTypes.clear()
            terrainPipelines.clear()
        }
    }

    /**
     * 返回本周期已经构造的变体，不触发任何 GPU 工作。
     * 示例：`pipelines()`。
     * @return 原版预编译可读取的独立管线列表
     */
    fun pipelines(): List<RenderPipeline> = pipelines.toList() + synchronized(terrainTypes) { terrainPipelines.toList() }

    /** 公共 Snippet 构造器保留任意宏字符串与额外绑定，避免拆解后遗漏自定义程序状态。 */
    internal fun copyBuilder(source: RenderPipeline): RenderPipeline.Builder {
        val snippet = RenderPipeline.Snippet(
            source.shaders,
            Optional.of(source.shaderDefines),
            Optional.of(source.bindGroupLayouts),
            source.colorTargetStates.toTypedArray(),
            source.colorTargetStates.size,
            Optional.ofNullable(source.depthStencilState),
            Optional.of(source.polygonMode),
            Optional.of(source.isCull),
            source.vertexFormatBindings.toTypedArray(),
            Optional.of(source.primitiveTopology),
            source.pushConstantSize(),
        )
        return RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipeline/render_type/${nextPipelineId++}"))
    }

    /**
     * 实体管线变体的完整缓存身份。
     * @property texture 实体纹理标识
     * @property brightness 已校验的亮度倍率
     * @property alpha 已限制范围的透明度
     * @property translucent 是否启用透明混合且不写深度
     */
    private data class EmissiveKey(val texture: Identifier, val brightness: Float, val alpha: Float, val translucent: Boolean)

    /**
     * 地形身份缓存必须同时隔离管线、基础层和批次。
     * @property pipeline 拥有实际地形效果的管线
     * @property baseLayer 原始层的共享身份
     * @property batchKey 几何批次身份
     */
    private data class TerrainIdentityKey(
        val pipeline: CooRenderPipeline<BlockState>,
        val baseLayer: RenderType,
        val batchKey: Any,
    )
}
