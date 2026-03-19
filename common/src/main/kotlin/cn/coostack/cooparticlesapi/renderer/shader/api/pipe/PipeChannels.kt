package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import java.util.function.Supplier

/**
 * shader pipe 输入输出通道集合抽象。
 */
interface PipeChannels {
    /**
     * 添加一个输入通道提供器。
     *
     * 添加顺序会影响后续在 shader 中对应的纹理槽位。
     */
    fun addChannel(id: Supplier<Int>): PipeChannels

    /**
     * 返回当前全部通道提供器。
     */
    fun getChannels(): List<Supplier<Int>>

    /**
     * 返回指定索引的通道提供器。
     */
    fun getChannel(index: Int): Supplier<Int>

    /**
     * 返回当前已经添加的通道数量。
     */
    fun currentInputCount(): Int

    /**
     * 在当前通道绑定好的上下文中执行绘制逻辑。
     */
    fun useOnContext(vertexDraw: Runnable): PipeChannels
}
