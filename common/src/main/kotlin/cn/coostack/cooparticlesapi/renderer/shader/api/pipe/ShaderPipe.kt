package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader

interface ShaderPipe {
    /**
     * 初始化渲染管道
     */
    fun init()

    fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe

    /**
     * 获取当前渲染管道拥有的framebuffer
     */
    fun fbo(): GlFrameBuffer

    /**
     * 使用mipmap
     */
    fun useMipmap(): ShaderPipe

    /**
     * 向这个pipe写入内容
     * 会经过当前的frag的处理
     */
    fun write(invoker: ShaderPipe.() -> Unit)

    /**
     * 从输入的channels读取内容到当前的fbo中
     *
     * @param channel 输入的channel
     */
    fun writeFromChannel(channel: PipeChannels): ShaderPipe

    /**
     * 输出当前pipe已经绘制的内容
     *
     * 通过pipe内的屏幕渲染器绘制 fbo
     * 在read时 会调用 handler执行渲染前操作
     */
    fun drawPipeFrame()

    /**
     * 将这个fbo绘制的内容打包输出
     */
    fun getFrameOutput(): PipeChannels

    fun resize(width: Int, height: Int)

    /**
     * 释放资源
     */
    fun release()
}