package cn.coostack.cooparticlesapi.renderer.model

import net.minecraft.resources.ResourceLocation

class RenderEntityModelPipe internal constructor(
    val id: String,
    val shader: ResourceLocation?,
    val postEffectType: ResourceLocation?,
    val params: Map<String, Any>,
    val graph: RenderEntityModelPipeGraph
)
