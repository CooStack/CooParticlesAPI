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
     * 设置纹理组件过滤模式
     */
    fun textureFilterMod(mod: Int): ShaderPipe

    /**
     * 向这个pipe写入内容
     */
    fun write(invoker: ShaderPipe.() -> Unit)

    /**
     * 这是将处理的结果存在当前的fbo中
     * 而 write是将处理前的结果存在当前的fbo中
     * 如果执行这个方法， 则意味着你的输入已经经过了当前 ShaderPipe frag着色器的处理
     * 所以可以直接获取output
     * @param channel 待处理的输入的颜色通道
     */
    fun writeFromChannel(channel: PipeChannels): ShaderPipe

    /**
     * 通过pipe内的屏幕渲染器绘制 fbo
     * 在read时 会调用 handler执行渲染前操作
     */
    fun drawPipeFrame()

    fun getFrameOutput(): PipeChannels


    fun resize(width: Int, height: Int)

    /**
     * 释放资源
     */
    fun release()
}