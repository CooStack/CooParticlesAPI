package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

/**
 * Small DSL for readable output -> input wiring.
 * Example: linker.from(outputPipe, 0).to(inputPipe, 1)
 */
class PipeLink(private val linker: PipeLinker, private val output: PipeLinkerNode) {
    fun to(inputPipe: ShaderPipe, inputChannel: Int = 0): PipeLinker {
        return linker.connect(output.pipe, output.channel, inputPipe, inputChannel)
    }

    fun to(input: PipeLinkerNode): PipeLinker {
        return linker.connect(output, input)
    }
}

fun PipeLinker.from(outputPipe: ShaderPipe, outputChannel: Int = 0): PipeLink {
    return PipeLink(this, PipeLinkerNode(outputPipe, outputChannel))
}

fun PipeLinker.from(output: PipeLinkerNode): PipeLink {
    return PipeLink(this, output)
}
