package cn.coostack.cooparticlesapi.renderer.model

class RenderEntityModelPipeGraphBuilder {
    private val edges = mutableListOf<RenderEntityModelPipeEdge>()

    fun node(pipeId: String, channel: String = "color"): RenderEntityModelPipeNode {
        return RenderEntityModelPipeNode(pipeId, channel)
    }

    fun connect(
        outputPipe: String,
        outputChannel: String,
        inputPipe: String,
        inputChannel: String
    ): RenderEntityModelPipeGraphBuilder {
        edges += RenderEntityModelPipeEdge(
            output = node(outputPipe, outputChannel),
            input = node(inputPipe, inputChannel)
        )
        return this
    }

    fun connect(
        output: RenderEntityModelPipeNode,
        input: RenderEntityModelPipeNode
    ): RenderEntityModelPipeGraphBuilder {
        edges += RenderEntityModelPipeEdge(output, input)
        return this
    }

    fun build(): RenderEntityModelPipeGraph {
        return RenderEntityModelPipeGraph(edges.toList())
    }
}
