package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import cn.coostack.cooparticlesapi.utils.api.ModelPartPointCollector
import cn.coostack.cooparticlesapi.entities.structure.client.StructureBlockRenderBridge

/**
 * 客户端类型的服务独立加载，专用服务器不能解析 PoseStack、ModelPart 或 RenderType。
 */
object CooClientServices {
    /** 按加载器提供结构模型渲染，保留 NeoForge 的模型数据与动态渲染层。 */
    @JvmField
    val STRUCTURE_BLOCK_RENDERER: StructureBlockRenderBridge =
        CooParticlesServices.load(StructureBlockRenderBridge::class.java)

    @JvmField
    val RENDER_TYPES_PROVIDER: CooRenderTypesProvider =
        CooParticlesServices.load(CooRenderTypesProvider::class.java)

    @JvmField
    val MODEL_PART_POINT_COLLECTOR: ModelPartPointCollector =
        CooParticlesServices.load(ModelPartPointCollector::class.java)
}
