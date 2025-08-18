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
     * 是否共用depth数据
     * @return false 不共用数据 在pipeManager就不会自动设置 depthAttachment的值
     */
    fun shareDepth(): Boolean

    /**
     * 设置纹理组件过滤模式
     */
    fun textureFilterMod(mod: Int): ShaderPipe

    /**
     * 向这个pipe写入内容
     */
    fun write(invoker: ShaderPipe.() -> Unit)

    /**
     * 通过pipe内的屏幕渲染器绘制 fbo
     * 在read时 会调用 handler执行渲染前操作
     */
    fun drawPipeFrame()

    /**
     * @param count 绘制次数 (>1 则将这个画面反复绘制)
     * 多次绘制用于节省pipe对象个数
     */
    fun setPipeRenderCount(count: Int): ShaderPipe

    fun resize(width: Int, height: Int)

    /**
     * 释放资源
     */
    fun release()
}