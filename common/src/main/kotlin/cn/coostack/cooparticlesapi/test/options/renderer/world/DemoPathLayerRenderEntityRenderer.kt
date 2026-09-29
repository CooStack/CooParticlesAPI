package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CooPathLayer
import cn.coostack.cooparticlesapi.gpudata.CooGpuDataBindingPoints
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformProvider
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderInput
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * # 路径图层共用示例 renderer
 *
 * 直接从 CParticle 路径约束使用的**同一个**路径图层 SSBO 读取预采样表并绘制：
 *
 * - shader 侧声明 `layout(std430, binding = 4) readonly buffer`，绑定点取
 *   [CooGpuDataBindingPoints.PATH_LAYER]，与 compute 路径约束完全一致。
 * - 渲染前调用 [CParticlePathLibrary.bind]，让本 renderer 不依赖同一帧内是否已经渲染过 GPU 粒子。
 * - 表头（负载基址、采样数、段类型）由 CPU 侧从图层读取后作为 uniform 下发，shader 不重复解析表头，
 *   也不为每条路径新增绑定点。
 *
 * 几何只给出**采样序号**：顶点位置写成 `(采样序号, 侧偏移, 0)`，顶点着色器按
 * `SAMPLE_STRIDE` 换算 SSBO 下标后取位置。路径改点不会让几何失效，只有采样数变化时才重建顶点。
 *
 * 路径为空或尚未分配图层区间时 `pathSampleCount <= 0`，renderer 直接跳过绘制。
 */
@CooAutoRegisterRenderer
class DemoPathLayerRenderEntityRenderer : RenderEntityRenderer<DemoPathLayerRenderEntity> {
    override val pipeline = CooPipelines.entity<DemoPathLayerRenderEntity>(id("render_entity/path_layer")) {
        world {
            vertex(id("core/vertex/render_entity_path_layer.vsh"))
            fragment(id("core/fragment/render_entity_path_layer.fsh"))
            uniform("pathBase") { entity: DemoPathLayerRenderEntity -> pathUniforms(entity).base }
            uniform("pathSampleCount") { entity: DemoPathLayerRenderEntity -> pathUniforms(entity).sampleCount }
            uniform("pathCurveType") { entity: DemoPathLayerRenderEntity -> pathUniforms(entity).curveType }
            uniform("time") { entity: DemoPathLayerRenderEntity -> entity.age.toFloat() }
            uniformValue(
                "lineColor",
                CooUniformProvider<DemoPathLayerRenderEntity> {
                    CooUniformValue.Vec4Value(0.35F, 0.85F, 1F, 0.85F)
                },
            )
        }
    }

    /** 上一次生成顶点所用的采样数；只有它变化时才重建模型，避免每帧重建几何。 */
    private var builtSampleCount = -1
    private var builtModel: RenderEntityModel? = null

    override fun render(input: RenderInput<DemoPathLayerRenderEntity>) {
        CParticlePathLibrary.bind()
        val uniforms = pathUniforms(input.entity)
        val sampleCount = uniforms.sampleCount.toInt()
        if (sampleCount <= 0) return
        val model = modelFor(sampleCount)
        RenderEntityModelExecutors.active().draw(model, input)
    }

    /**
     * 读取路径负载表头。
     *
     * 图层是共享资源，几何与预采样表都在里面；这里只读取渲染需要的三个数，
     * 不从 RenderEntity 自己的字段重建路径。
     */
    private fun pathUniforms(entity: DemoPathLayerRenderEntity): PathUniforms {
        val slot = entity.path ?: return PathUniforms.EMPTY
        if (slot.released || slot.base <= 0) return PathUniforms.EMPTY
        val layer = CParticlePathLibrary.currentLayerData() ?: return PathUniforms.EMPTY
        if (slot.base + CooPathLayer.CooPathPayload.H_SAMPLE_COUNT >= layer.size) return PathUniforms.EMPTY
        val sampleCount = layer[slot.base + CooPathLayer.CooPathPayload.H_SAMPLE_COUNT].toInt()
        val curveType = layer[slot.base + CooPathLayer.CooPathPayload.H_SEGMENT_TYPE].toInt()
        val pointCount = layer[slot.base + CooPathLayer.CooPathPayload.H_POINT_COUNT].toInt()
        if (sampleCount <= 0 || pointCount < 2) return PathUniforms.EMPTY
        val sampleBase = slot.base + CooPathLayer.CooPathPayload.sampleBase(pointCount)
        return PathUniforms(sampleBase.toFloat(), sampleCount.toFloat(), curveType.toFloat())
    }

    /**
     * 把路径采样表画成一条折线。
     *
     * 每个采样点输出两个顶点：`(序号, -1, 0)` 与 `(序号, +1, 0)`。相邻采样点的同侧顶点组成一条线，
     * 因此每两个相邻采样点产生两条线，形成一条带宽度的带状折线。
     */
    private fun modelFor(sampleCount: Int): RenderEntityModel {
        builtModel?.takeIf { builtSampleCount == sampleCount }?.let { return it }
        val model = RenderEntityModelBuilder()
        val layer = model.layer("path_layer")
        val color = Vector4f(1F, 1F, 1F, 1F)
        for (index in 0 until sampleCount - 1) {
            for (side in SIDES) {
                model.addVertex(layer, Vector3f(index.toFloat(), side, 0F), color, Vector2f(0F, side))
                model.addVertex(layer, Vector3f((index + 1).toFloat(), side, 0F), color, Vector2f(1F, side))
            }
        }
        return model.build().also {
            builtModel = it
            builtSampleCount = sampleCount
        }
    }

    /** 渲染一次路径所需的表头数据。 */
    private data class PathUniforms(
        val base: Float,
        val sampleCount: Float,
        val curveType: Float,
    ) {
        companion object {
            /** 没有可用路径时的空表头；`sampleCount <= 0` 会让 render 直接跳过绘制。 */
            val EMPTY = PathUniforms(0F, 0F, 0F)
        }
    }

    private companion object {
        /** 折线的两侧偏移；两条线合起来给路径一点视觉宽度。 */
        val SIDES = floatArrayOf(-1F, 1F)

        fun id(path: String): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
