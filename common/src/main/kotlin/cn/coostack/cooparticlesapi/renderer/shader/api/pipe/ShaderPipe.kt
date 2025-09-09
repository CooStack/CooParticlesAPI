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
     * 会将channel的内容经过当前pipe的片段着色器处理，然后存储到当前的fbo材质通道中
     * @param channel 待处理的输入的颜色通道
     */
    fun writeFromChannel(channel: PipeChannels): ShaderPipe

    /**
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