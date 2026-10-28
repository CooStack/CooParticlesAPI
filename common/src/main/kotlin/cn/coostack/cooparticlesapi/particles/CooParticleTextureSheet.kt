package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.mixin.ParticleRenderTypeCreateInvoker
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import java.util.Optional

/**
 * 将 Coo 普通粒子图层注册到 26.3 的 RenderPearl 管线和延迟几何提交。
 *
 * 图层身份和名称供粒子分组及网络序列化共用；只在客户端初始化阶段注册。
 * 示例：`particle.textureSheet = CooParticleTextureSheet.ADDITION_BLEND_TRANSLUCENT`。
 * 不直接修改 OpenGL 状态，Iris 的自定义管线支持仍须按实际版本验证。
 */
object CooParticleTextureSheet {
    /** 按注册顺序保存的共享图层身份，外部只能读取副本。 */
    private val registeredSheets = mutableListOf<ParticleRenderType>()
    /** 图层身份到不可变资源绑定的注册表。 */
    private val sheetLayers = linkedMapOf<ParticleRenderType, SheetLayer>()
    /** 网络名称到共享图层身份的索引。 */
    private val sheetNames = linkedMapOf<String, ParticleRenderType>()
    /** 交给原版资源加载阶段预编译的管线注册表。 */
    private val pipelines = mutableListOf<RenderPipeline>()

    /** 光照图粒子图层，透明混合并写入深度。 */
    val PARTICLE_SHEET_LIT = registerSheet(
        "PARTICLE_SHEET_LIT",
        BlendFunction.TRANSLUCENT,
        true,
    )
    /** 不透明粒子图层，关闭混合并写入深度。 */
    val PARTICLE_SHEET_OPAQUE = registerSheet(
        "PARTICLE_SHEET_OPAQUE",
        null,
        true,
    )
    /** 透明混合粒子图层，保留旧版深度写入语义。 */
    val PARTICLE_SHEET_TRANSLUCENT = registerSheet(
        "PARTICLE_SHEET_TRANSLUCENT",
        BlendFunction.TRANSLUCENT,
        true,
    )
    /** 透明混合但不写入深度的粒子图层。 */
    val PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE = registerSheet(
        "PARTICLE_SHEET_TRANSLUCENT_NO_DEPTH_WRITE",
        BlendFunction.TRANSLUCENT,
        false,
    )
    /** 使用方块图集且不混合的落尘图层。 */
    val TERRAIN_SHEET = registerSheet(
        "TERRAIN_SHEET",
        null,
        true,
        TextureAtlas.LOCATION_BLOCKS,
    )
    /** 保留粒子生命周期而不提交几何的共享图层。 */
    val NO_RENDER = registerSheet(
        "NO_RENDER",
        null,
        true,
        render = false,
    )
    /** 供旧调用方使用的默认自定义透明图层。 */
    val CUSTOM = registerSheet(
        "CUSTOM",
        BlendFunction.TRANSLUCENT,
        true,
    )

    /** 源色与目标色直接相加，写入深度。 */
    val ADDITION_BLEND = registerSheet(
        "ADDITION_BLEND",
        BlendFunction(BlendFactor.ONE, BlendFactor.ONE),
        true,
    )
    /** 按源透明度加色，写入深度。 */
    val ADDITION_BLEND_TRANSLUCENT = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT",
        BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.SRC_ALPHA, BlendFactor.ONE),
        true,
    )
    /** 按源透明度加色，不写入深度。 */
    val ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT_NO_DEPTH_WRITE",
        BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.SRC_ALPHA, BlendFactor.ONE),
        false,
    )
    /** 限制源色范围的滤色混合，保留目标透明度并写入深度。 */
    val ADDITION_BLEND_NOT_HDR = registerSheet(
        "ADDITION_BLEND_NOT_HDR",
        BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE),
        true,
        boundedAlpha = true,
    )
    /** 限制源色范围的滤色混合，保留目标透明度且不写入深度。 */
    val ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE = registerSheet(
        "ADDITION_BLEND_NOT_HDR_NO_DEPTH_WRITE",
        BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE),
        false,
        boundedAlpha = true,
    )
    /** 按源透明度预乘的有界滤色图层，写入深度。 */
    val ADDITION_BLEND_TRANSLUCENT_NOT_HDR = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT_NOT_HDR",
        BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE),
        true,
        boundedAlpha = true,
    )
    /** 按源透明度预乘的有界滤色图层，不写入深度。 */
    val ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE = registerSheet(
        "ADDITION_BLEND_TRANSLUCENT_NOT_HDR_NO_DEPTH_WRITE",
        BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE),
        false,
        boundedAlpha = true,
    )

    /** 触发共享图层初始化；实际着色器由原版管线资源生命周期管理。 */
    @JvmStatic
    fun init() = Unit

    /**
     * 保留旧版资源重载入口，26.3 不再自行创建或销毁着色器程序。
     *
     * @param resourceManager 当前重载的资源管理器，管线资源由原版读取
     */
    @JvmStatic
    fun reloadShader(resourceManager: ResourceManager) = Unit

    /** 保留旧版释放入口，避免释放由原版与可选 Iris 路径拥有的程序。 */
    @JvmStatic
    fun releaseShader() = Unit

    /**
     * 注册外部图层的默认透明绘制绑定；同一身份重复注册无副作用。
     *
     * @param type 具有稳定网络名称的共享图层身份
     * @return 原样返回 [type]
     */
    @JvmStatic
    fun register(type: ParticleRenderType): ParticleRenderType {
        if (type !in sheetLayers) {
            registerSheet(type.name(), BlendFunction.TRANSLUCENT, true, type = type)
        }
        return type
    }

    /**
     * 为旧调用方保留的图层注册入口。
     *
     * @param type 待注册的共享图层身份
     * @return 已注册的 [type]
     */
    @JvmStatic
    fun registerRenderType(type: ParticleRenderType): ParticleRenderType = register(type)

    /** @return 按注册顺序复制的图层列表，不暴露注册表的可变所有权 */
    @JvmStatic
    fun getSheets(): List<ParticleRenderType> = registeredSheets.toList()

    /** @return 供原版预编译的管线副本 */
    @JvmStatic
    fun getPipelines(): List<RenderPipeline> = pipelines.toList()

    /**
     * @param type 已注册的图层身份
     * @return 原版命令收集器接受的绘制类型；不绘制或未知图层返回 null
     */
    @JvmStatic
    fun getRenderType(type: ParticleRenderType): RenderType? {
        val layer = sheetLayers[type] ?: return null
        val renderType = layer.renderType ?: return null
        return if (layer.boundedAlpha) renderType else IrisCompat.wrapParticleRenderType(renderType)
    }

    /**
     * @param type 请求的图层身份
     * @return 图层资源绑定；未知图层使用默认光照图层
     */
    @JvmStatic
    fun getLayer(type: ParticleRenderType): SheetLayer =
        sheetLayers[type] ?: sheetLayers[PARTICLE_SHEET_LIT]!!

    /**
     * @param name 网络中保存的稳定图层名称
     * @return 对应的共享图层；未知名称返回 null
     */
    @JvmStatic
    fun textureSheetFromString(name: String): ParticleRenderType? = sheetNames[name]

    /** 创建并记录图层、管线与网络名称；混合和深度行为由调用方明确选择。 */
    private fun registerSheet(
        name: String,
        blend: BlendFunction?,
        depthWrite: Boolean,
        atlas: Identifier = TextureAtlas.LOCATION_PARTICLES,
        render: Boolean = true,
        boundedAlpha: Boolean = false,
        type: ParticleRenderType = ParticleRenderType(name, name.take(2)),
    ): ParticleRenderType {
        if (type in sheetLayers) return type
        val pipeline = createPipeline(name, blend, depthWrite, boundedAlpha)
        if (render && !boundedAlpha) {
            IrisCompat.assignPipeline(pipeline, if (blend == null) "PARTICLES" else "PARTICLES_TRANSLUCENT")
        }
        val renderType = if (render) {
            val setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", atlas)
                .useLightmap()
                .apply { if (blend != null) sortOnUpload() }
                .createRenderSetup()
            ParticleRenderTypeCreateInvoker.createParticleRenderType(name, setup)
        } else {
            null
        }
        sheetLayers[type] = SheetLayer(atlas, pipeline, renderType, boundedAlpha, depthWrite)
        sheetNames[name] = type
        if (type !in registeredSheets) registeredSheets.add(type)
        if (pipeline !in pipelines) pipelines.add(pipeline)
        ControlableParticleData.registerRenderType(type)
        return type
    }

    /** 沿用原版粒子的顶点格式、光照和雾绑定，并显式保留旧图层混合语义。 */
    private fun createPipeline(
        name: String,
        blend: BlendFunction?,
        depthWrite: Boolean,
        boundedAlpha: Boolean,
    ): RenderPipeline {
        val fragment = if (boundedAlpha) {
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/coo_particle_screen")
        } else {
            Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/coo_particle")
        }
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipeline/controlable_particle/${name.lowercase()}"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/particle")
            .withFragmentShader(fragment)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, depthWrite))
            .withColorTargetState(
                blend?.let(::ColorTargetState)
                    ?: ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL),
            )
            .build()
    }

    /**
     * 一次注册后不变的图层资源绑定。
     *
     * @property atlas 图层使用的图集标识
     * @property pipeline 由原版管理和预编译的 RenderPearl 管线
     * @property renderType 命令收集器绘制类型，不绘制时为 null
     * @property boundedAlpha 是否必须保留自定义片元中的颜色限制与 Alpha 预乘
     * @property depthWrite 是否在当前图层写入场景深度
     */
    data class SheetLayer(
        val atlas: Identifier,
        val pipeline: RenderPipeline,
        val renderType: RenderType?,
        val boundedAlpha: Boolean,
        val depthWrite: Boolean,
    )
}
