package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import cn.coostack.cooparticlesapi.utils.api.ModelPartPointCollector

/**
 * Services whose types are client-only must not be initialized from the common
 * service registry.  Keep this object on the client call path so dedicated
 * servers never resolve PoseStack, ModelPart, or RenderType.
 */
object CooClientServices {
    @JvmField
    val RENDER_TYPES_PROVIDER: CooRenderTypesProvider =
        CooParticlesServices.load(CooRenderTypesProvider::class.java)

    @JvmField
    val MODEL_PART_POINT_COLLECTOR: ModelPartPointCollector =
        CooParticlesServices.load(ModelPartPointCollector::class.java)
}
