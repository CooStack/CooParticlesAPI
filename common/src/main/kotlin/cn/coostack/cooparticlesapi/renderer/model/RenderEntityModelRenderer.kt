package cn.coostack.cooparticlesapi.renderer.model

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer

interface RenderEntityModelRenderer<T : RenderEntity> : RenderEntityRenderer<T> {
    fun buildModel(entity: T, tickDelta: Float): RenderEntityModel
}
