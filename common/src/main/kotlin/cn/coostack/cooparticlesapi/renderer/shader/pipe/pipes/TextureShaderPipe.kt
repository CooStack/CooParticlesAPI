// TextureShaderPipe.kt 完整实现
package cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTextures
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.FramePipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import org.lwjgl.opengl.GL33.*
import java.util.function.Supplier

class TextureShaderPipe(private val textures: GlTextures) : ShaderPipe {
    companion object {
        val screenBuffer = VertexBuffers.getScreenBuffer()
    }

    private var frameBuffer: GlFrameBuffer = SimpleFrameBuffer(textures.getTextureCounts()) { -1 }
    private var handlers = mutableListOf<ShaderProgramUploader>()
    private var mipmapEnabled = false

    override fun init() {
        textures.init()
        screenBuffer.init()
        frameBuffer.init()
    }

    override fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe {
        handlers.add(handler)
        return this
    }

    override fun fbo(): GlFrameBuffer {
        return frameBuffer
    }

    override fun useMipmap(): ShaderPipe {
        mipmapEnabled = true
        return this
    }

    override fun write(invoker: ShaderPipe.() -> Unit) {
        // 绑定帧缓冲区
        fbo().writeFrameBufferWith {
            invoker()
        }

        if (mipmapEnabled) {
            textures.use()
            glGenerateMipmap(GL_TEXTURE_2D)
            textures.reset()
        }
    }

    override fun writeFromChannel(channel: PipeChannels): ShaderPipe {
        // 从其他管道的输出通道获取纹理并渲染

        fbo().writeFrameBufferWith {
            // 使用纹理
            channel.useOnContext {
                // 绘制全屏四边形
                screenBuffer.draw()
            }
        }

        return this
    }

    override fun drawPipeFrame() {
        // 绘制当前管道的纹理到屏幕（用于调试）
        val screenBuffer = VertexBuffers.getScreenBuffer()

        // 创建临时着色器程序用于显示纹理
        // 这里需要根据你的实际情况实现
        textures.drawWith {
            screenBuffer.draw()
        }
    }

    override fun getFrameOutput(): PipeChannels {
        // 返回当前管道的纹理作为输出通道
        val channels = FramePipeChannels()

        // 获取纹理ID并添加到通道
        // 注意：这里假设纹理只有一个，实际情况可能需要支持多个
        channels.addChannel {
            // 从textures中获取第一个纹理的ID
            // 你需要根据你的GlTextures实现来获取纹理ID
            textures.use()
            val textureID = glGetInteger(GL_TEXTURE_BINDING_2D)
            textures.reset()
            textureID
        }

        return channels
    }

    override fun resize(width: Int, height: Int) {
        fbo().resize(width, height)
    }

    override fun release() {
        frameBuffer.release()
        screenBuffer.release()
    }
}