package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.display.CooRenderTypePipelineFactory
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager

/**
 * 库内基础、发光和实体裁剪程序的 RenderPearl 管线描述。
 *
 * GPU 程序由原版 PipelineCache 编译、重载和释放，不再持有旧 ShaderInstance。
 * Iris 兼容包装由公共 RenderType 工厂保留，不能由管线构造成功推断光影运行兼容。
 */
object MCShaders {
    /** 仅使用位置、颜色与原版矩阵绑定的基础程序。 */
    val POSITION_COLOR: RenderPipeline = basePipeline("position_color", false).build()

    /** 保留颜色八倍输出的发光程序；实际 HDR 范围取决于活动颜色附件。 */
    val GLOW: RenderPipeline = basePipeline("coo_glow", false).build()

    /** 亮度与透明度均为一的实体裁剪发光程序。 */
    val ENTITY_CUTOUT_EMISSIVE: RenderPipeline = basePipeline("coo_entity_cutout_emissive", true).build()

    /**
     * 重载时清除依赖自定义供应器的类型缓存，程序资源仍由原版管理。
     * 示例：`init(resourceManager)`。
     * @param resourceManager 当前资源包的管理器，原版负责从中读取管线源码
     */
    fun init(resourceManager: ResourceManager) {
        CooRenderTypePipelineFactory.clearCaches()
    }

    /** 释放库自身的缓存引用，不关闭由原版或 Iris 拥有的 GPU 程序。 */
    fun release() {
        CooRenderTypePipelineFactory.clearCaches()
    }

    /**
     * 供原版资源重载阶段预编译的固定管线。
     * 示例：`pipelines()`。
     * @return 不暴露内部可变集合的固定管线列表
     */
    fun pipelines(): List<RenderPipeline> = listOf(POSITION_COLOR, GLOW, ENTITY_CUTOUT_EMISSIVE)

    /** 按程序实际声明建立矩阵与纹理布局，不添加未绑定的自定义 uniform。 */
    private fun basePipeline(shader: String, entity: Boolean): RenderPipeline.Builder =
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipeline/$shader"))
            .withVertexShader(shaderResource(shader))
            .withFragmentShader(shaderResource(shader))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .apply {
                if (entity) {
                    withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
                    withShaderDefine("COO_BRIGHTNESS", 1F)
                    withShaderDefine("COO_ALPHA", 1F)
                }
            }
            .withVertexBinding(0, if (entity) DefaultVertexFormat.ENTITY else DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)

    /** 只复用原版基础程序，库内自定义程序保持在本模组命名空间。 */
    private fun shaderResource(shader: String): Identifier =
        Identifier.fromNamespaceAndPath(
            if (shader == "position_color") "minecraft" else CooParticlesConstants.MOD_ID,
            "core/$shader",
        )
}
