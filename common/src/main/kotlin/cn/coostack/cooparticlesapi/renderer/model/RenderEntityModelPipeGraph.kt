package cn.coostack.cooparticlesapi.renderer.model

class RenderEntityModelPipeGraph internal constructor(
    val edges: List<RenderEntityModelPipeEdge>
) {
    fun inputsOf(pipeId: String): List<RenderEntityModelPipeNode> {
        return edges.filter { it.input.pipeId == pipeId }.map { it.output }
    }
}
