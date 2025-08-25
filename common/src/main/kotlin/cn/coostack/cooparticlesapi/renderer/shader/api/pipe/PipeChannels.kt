package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import java.util.function.Supplier

interface PipeChannels {
    /**
     * 设置一个channel
     * 设置顺序和通道ID有关
     * @param id 获取材质通道的提供器
     */
    fun addChannel(id: Supplier<Int>): PipeChannels

    fun getChannels(): List<Supplier<Int>>

    fun getChannel(index: Int): Supplier<Int>

    /**
     * 当前已经添加的材质通道
     */
    fun currentInputCount(): Int

    /**
     * 绑定输入的input
     * 需要自行绑定对应的通道ID
     * 绑定材质后，在内进行着色器程序的绑定，然后绘制到对应的fbo输出通道中
     */
    fun drawWith(vertexDraw: Runnable): PipeChannels
}