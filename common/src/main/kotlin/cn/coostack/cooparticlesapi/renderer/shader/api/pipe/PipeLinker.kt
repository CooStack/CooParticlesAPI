package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

/**
 * shader pipe 有向图连接器。
 *
 * 用于描述“哪个 pipe 的哪个输出通道，连接到另一个 pipe 的哪个输入通道”。
 */
interface PipeLinker {

    /**
     * @param inputPipe 需要输入的渲染管线
     * @param inputChannel 需要输入的渲染管线的通道索引
     * @param outputPipe 渲染管线通道的提供者
     * @param outputChannel 渲染管线提供的具体通道索引
     */
    fun link(inputPipe: ShaderPipe, inputChannel: Int, outputPipe: ShaderPipe, outputChannel: Int): PipeLinker

    /**
     * 使用 `(pipe, channel)` 对形式建立连接。
     */
    fun link(input: Pair<ShaderPipe, Int>, output: Pair<ShaderPipe, Int>): PipeLinker

    /**
     * 使用 `PipeLinkerNode` 建立连接。
     */
    fun link(input: PipeLinkerNode, output: PipeLinkerNode): PipeLinker

    /**
     * 以更符合阅读习惯的 “output -> input” 顺序建立连接。
     */
    fun connect(outputPipe: ShaderPipe, outputChannel: Int, inputPipe: ShaderPipe, inputChannel: Int): PipeLinker {
        return link(inputPipe, inputChannel, outputPipe, outputChannel)
    }

    /**
     * `connect(...)` 的 `PipeLinkerNode` 重载。
     */
    fun connect(output: PipeLinkerNode, input: PipeLinkerNode): PipeLinker {
        return connect(output.pipe, output.channel, input.pipe, input.channel)
    }

    /**
     * 查找某个输入 pipe 的全部上游提供者。
     *
     * 返回值中：
     * - key 是输入通道索引
     * - value 是对应的上游节点
     */
    fun findAllChannel(input: ShaderPipe): Map<Int, PipeLinkerNode>
}
