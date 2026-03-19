package cn.coostack.cooparticlesapi.renderer.shader.api.pipe

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader

/**
 * shader pipe 抽象。
 *
 * 一个 pipe 通常对应“输入若干纹理通道，经过一个 shader 处理后写入自己的 FBO”。
 */
interface ShaderPipe {
    /**
     * 初始化当前渲染管道。
     */
    fun init()

    /**
     * 添加一个在绘制前上传 program 数据的处理器。
     */
    fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe

    /**
     * 返回当前 pipe 使用的 framebuffer。
     */
    fun fbo(): GlFrameBuffer

    /**
     * 启用 mipmap 模式。
     */
    fun useMipmap(): ShaderPipe

    /**
     * 在当前 pipe 的写入上下文中执行自定义写入逻辑。
     */
    fun write(invoker: ShaderPipe.() -> Unit)

    /**
     * 把输入通道内容读取并处理后写入当前 FBO。
     */
    fun writeFromChannel(channel: PipeChannels): ShaderPipe

    /**
     * 将当前 pipe 的 FBO 内容真正绘制出来。
     */
    fun drawPipeFrame()

    /**
     * 把当前 FBO 的输出包装成可继续传递的 `PipeChannels`。
     */
    fun getFrameOutput(): PipeChannels

    /**
     * 调整当前 pipe 的内部尺寸。
     */
    fun resize(width: Int, height: Int)

    /**
     * 释放当前 pipe 持有的资源。
     */
    fun release()
}
