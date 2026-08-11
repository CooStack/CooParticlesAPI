package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState

/** 实体、方块和通用渲染图的公开入口。 */
object CooPipelines {
    /** 不执行后处理的实体 pipeline 模板。 */
    @JvmField
    val DEFAULT: CooRenderPipeline<Nothing> = CooRenderPipelineBuilder<Nothing>(
        id("default"),
        CooPipelineDomain.ENTITY
    ).build()

    /**
     * 共享几何 mask bloom 模板。
     *
     * preset 本身就是普通节点和 line 组成的 DAG，compiler 不包含 bloom 专用分支。
     */
    @JvmField
    val MASK_BLOOM: CooRenderPipeline<Nothing> = CooRenderPipelineBuilder<Nothing>(
        id("mask_bloom"),
        CooPipelineDomain.ENTITY
    ).apply {
        postInScene()
        val geometry = world("geometry") {
            vertex(id("core/vertex/render_entity_model.vsh"))
            fragment(id("core/fragment/render_entity_model.fsh"))
            outputFormat(CooTextureFormat.RGBA16F)
            maskOutput()
        }
        val extract = pass("bloom_extract") {
            fragment(id("post/bloom_bright_extract.fsh"))
            input("scene", format = CooTextureFormat.RGBA16F)
            outputFormat(CooTextureFormat.RGBA16F)
            // 保留完整采样链，最高级 tile 才能按导数在相邻 mip 之间过滤。
            mipLevels(12)
            uniform("threshold", 0F)
            uniform("softKnee", 0.5F)
            uniform("PremultipliedInput", CooUniformValue.BoolValue(true))
            uniform("Intensity", 3F)
        }
        // BSL 一次构建 7 级 atlas；采样核按分辨率计算，不再暴露连续模糊参数。
        val bloomBslAtlas = pass("bloom_bsl_atlas") {
            fragment(id("post/bloom_bsl_atlas.fsh"))
            input("BloomInput", format = CooTextureFormat.RGBA16F, mipLevels = 12)
            outputFormat(CooTextureFormat.RGBA16F)
            uniform("BloomLevels", CooUniformValue.IntValue(7))
        }
        val composite = pass("composite") {
            fragment(id("post/mask_bloom_composite.fsh"))
            input("SceneColor")
            input("BloomAtlas", format = CooTextureFormat.RGBA16F)
            uniform("MipLevels", CooUniformValue.IntValue(7))
            outputFormat(CooTextureFormat.RGBA8)
        }

        line(geometry.color(), worldTarget())
        line(geometry.mask(), extract.input("scene"))
        line(extract.color(), bloomBslAtlas.input("BloomInput"))
        line(sceneColor(), composite.input("SceneColor"))
        line(bloomBslAtlas.color(), composite.input("BloomAtlas"))
        line(composite.color(), screenTarget())

        parameter("bloomThreshold", extract, "threshold")
        parameter("bloomSoftKnee", extract, "softKnee")
        parameter("intensity", extract, "Intensity")
        parameter("bloomMipLevels", bloomBslAtlas, "BloomLevels")
        parameter("bloomMipLevels", composite, "MipLevels")
    }.build()

    /** 保持原版 terrain 行为的方块 pipeline 模板。 */
    @JvmField
    val BLOCK_DEFAULT: CooRenderPipeline<BlockState> = CooRenderPipelineBuilder<BlockState>(
        id("block_default"),
        CooPipelineDomain.BLOCK
    ).build()

    /**
     * 执行 `CooPipelines` 定义的 `entity` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`entity(id = id, block = block)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun <T : RenderEntity> entity(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<T>.() -> Unit
    ): CooRenderPipeline<T> {
        return CooRenderPipelineBuilder<T>(id, CooPipelineDomain.ENTITY).apply(block).build()
    }

    /**
     * 执行 `CooPipelines` 定义的 `block` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`block(id = id, block = block)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun block(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<BlockState>.() -> Unit
    ): CooRenderPipeline<BlockState> {
        val pipeline = CooRenderPipelineBuilder<BlockState>(id, CooPipelineDomain.BLOCK).apply(block).build()
        return CooTerrainEffectManager.register(pipeline)
    }

    /**
     * 根据输入和 `CooPipelines` 当前配置创建 `generic` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`generic(id = id, block = block)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 根据当前输入生成的新对象或数据结果
     */
    fun <T : Any> generic(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<T>.() -> Unit
    ): CooRenderPipeline<T> {
        return CooRenderPipelineBuilder<T>(id, CooPipelineDomain.GENERIC).apply(block).build()
    }

    /**
     * 执行 `CooPipelines` 定义的 `screen` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`screen(id = id, block = block)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @param block 在当前生命周期或数据上下文中执行的回调
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    internal fun screen(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<Any>.() -> Unit
    ): CooRenderPipeline<Any> {
        return CooRenderPipelineBuilder<Any>(id, CooPipelineDomain.SCREEN).apply(block).build()
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
