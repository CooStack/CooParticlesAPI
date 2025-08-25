package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

/**
 * 使用有向图制作
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
     * @param input 输入的渲染管线
     * @param output 提供内容的渲染管线
     */
    fun link(input: Pair<ShaderPipe, Int>, output: Pair<ShaderPipe, Int>): PipeLinker

    /**
     * @param input 输入的渲染管线
     * @param output 提供内容的渲染管线
     */
    fun link(input: PipeLinkerNode, output: PipeLinkerNode): PipeLinker

    /**
     * 找到这个渲染管线的所有提供者
     * @param input 需要输入的渲染管线
     * @return 索引代表输入的通道 值代表提供者
     */
    fun findAllChannel(input: ShaderPipe): Map<Int, PipeLinkerNode>


}