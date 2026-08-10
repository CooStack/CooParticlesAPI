package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
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
        val geometry = world("geometry") {
            vertex(id("core/vertex/render_entity_model.vsh"))
            fragment(id("core/fragment/render_entity_model.fsh"))
            maskOutput()
            uniform("BloomIntensity", 3F)
        }
        val blurHorizontal = pass("blur_horizontal") {
            fragment(id("post/bloom_blur_horizontal.fsh"))
            input("Input")
            uniform("Sigma", 14F)
            uniform("Range", 10F)
        }
        val blurVertical = pass("blur_vertical") {
            fragment(id("post/bloom_blur_vertical.fsh"))
            input("Input")
            uniform("Sigma", 14F)
            uniform("Range", 10F)
        }
        val composite = pass("composite") {
            fragment(id("post/mask_bloom_composite.fsh"))
            input("SceneColor")
            input("Bloom")
            uniform("Intensity", 1F)
        }

        line(geometry.color(), worldTarget())
        line(geometry.mask(), blurHorizontal.input("Input"))
        line(blurHorizontal.color(), blurVertical.input("Input"))
        line(sceneColor(), composite.input("SceneColor"))
        line(blurVertical.color(), composite.input("Bloom"))
        line(composite.color(), screenTarget())

        parameter("blurSigma", blurHorizontal, "Sigma")
        parameter("blurSigma", blurVertical, "Sigma")
        parameter("blurRange", blurHorizontal, "Range")
        parameter("blurRange", blurVertical, "Range")
        parameter("intensity", geometry, "BloomIntensity")
    }.build()

    /** 保持原版 terrain 行为的方块 pipeline 模板。 */
    @JvmField
    val BLOCK_DEFAULT: CooRenderPipeline<BlockState> = CooRenderPipelineBuilder<BlockState>(
        id("block_default"),
        CooPipelineDomain.BLOCK
    ).build()

    fun <T : RenderEntity> entity(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<T>.() -> Unit
    ): CooRenderPipeline<T> {
        return CooRenderPipelineBuilder<T>(id, CooPipelineDomain.ENTITY).apply(block).build()
    }

    fun block(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<BlockState>.() -> Unit
    ): CooRenderPipeline<BlockState> {
        val pipeline = CooRenderPipelineBuilder<BlockState>(id, CooPipelineDomain.BLOCK).apply(block).build()
        return CooTerrainEffectManager.register(pipeline)
    }

    fun <T : Any> generic(
        id: ResourceLocation,
        block: CooRenderPipelineBuilder<T>.() -> Unit
    ): CooRenderPipeline<T> {
        return CooRenderPipelineBuilder<T>(id, CooPipelineDomain.GENERIC).apply(block).build()
    }

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
