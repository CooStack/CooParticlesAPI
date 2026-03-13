package cn.coostack.cooparticlesapi.renderer.shader.pipe.manager

import cn.coostack.cooparticlesapi.exceptions.RenderPipeInputException
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeLinker
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeLinkerNode
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe

class GraphPipeLinker : PipeLinker {
    /**
     * key 是 output
     * value 是 input
     */
    private val pipeQueue = HashMap<PipeLinkerNode, MutableList<PipeLinkerNode>>()

    /**
     * 写入顺序
     * outputPipe 将outputChannel渲染数据写入 inputPipe的 inputChannel
     *
     * @param inputPipe
     * @param inputChannel
     * @param outputPipe
     * @param outputChannel
     * @return
     */
    override fun link(
        inputPipe: ShaderPipe,
        inputChannel: Int,
        outputPipe: ShaderPipe,
        outputChannel: Int
    ): PipeLinker {
        return link(PipeLinkerNode(inputPipe, inputChannel), PipeLinkerNode(outputPipe, outputChannel))
    }

    override fun link(
        input: Pair<ShaderPipe, Int>,
        output: Pair<ShaderPipe, Int>
    ): PipeLinker {
        link(
            PipeLinkerNode(input.first, input.second),
            PipeLinkerNode(output.first, output.second),
        )
        return this
    }

    override fun link(
        input: PipeLinkerNode,
        output: PipeLinkerNode
    ): PipeLinker {
        if (hasLinked(input)) {
            throw RenderPipeInputException(input.pipe.fbo().fbo(), input.channel)
        }
        val channelSize = output.pipe.fbo().getOutputChannelCount()
        if (channelSize <= output.channel) {
            throw ArrayIndexOutOfBoundsException("output没有那么多channel 提供的输出通道个数:${channelSize} 你的输入${output.channel}")
        }
        pipeQueue.getOrPut(output) { ArrayList() }.add(input) // 链接成功 -> output 输出给 input
        return this
    }

    override fun findAllChannel(input: ShaderPipe): Map<Int, PipeLinkerNode> {
        val res = HashMap<Int, PipeLinkerNode>()
        pipeQueue.forEach { entry ->
            entry.value.forEach {
                if (it.pipe == input) {
                    res[it.channel] = entry.key
                }
            }
        }
        return res
    }


    private fun hasLinked(target: PipeLinkerNode): Boolean {
        return pipeQueue.any {
            it.value.contains(target) // target 已经被某人链接了
        }
    }

    fun clear() {
        pipeQueue.clear()
    }
}
