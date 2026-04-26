package cn.coostack.cooparticlesapi.renderer.model

import net.minecraft.resources.ResourceLocation

class RenderEntityModelPipeBuilder internal constructor(
    private val id: String
) {
    private var shader: ResourceLocation? = null
    private var postEffectType: ResourceLocation? = null
    private val params = linkedMapOf<String, Any>()
    private var graph = RenderEntityModelPipeGraph(emptyList())

    fun shader(id: ResourceLocation): RenderEntityModelPipeBuilder {
        shader = id
        return this
    }

    fun post(type: ResourceLocation): RenderEntityModelPipeBuilder {
        postEffectType = type
        return this
    }

    fun param(name: String, value: Any): RenderEntityModelPipeBuilder {
        params[name] = value
        return this
    }

    fun graph(block: RenderEntityModelPipeGraphBuilder.() -> Unit): RenderEntityModelPipeBuilder {
        graph = RenderEntityModelPipeGraphBuilder().apply(block).build()
        return this
    }

    fun build(): RenderEntityModelPipe {
        return RenderEntityModelPipe(
            id = id,
            shader = shader,
            postEffectType = postEffectType,
            params = params.toMap(),
            graph = graph
        )
    }
}
