package cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes

import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.FramePipeChannels
import java.util.function.Supplier

/**
 * 只是传递一个 depth属性
 */
class OutputDepthPipe(val depthSupplier: Supplier<Int>) : ShaderPipe {
    private val pipeOutput = FramePipeChannels()
    override fun init() {
        pipeOutput.addChannel(depthSupplier)
    }

    override fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe {
        return this
    }

    override fun fbo(): GlFrameBuffer {
        throw IllegalAccessException("深度pipe不能作为输入")
    }

    override fun textureFilterMod(mod: Int): ShaderPipe {
        return this
    }

    override fun write(invoker: ShaderPipe.() -> Unit) {

    }

    override fun writeFromChannel(channel: PipeChannels): ShaderPipe {
        return this
    }

    override fun drawPipeFrame() {
    }

    override fun getFrameOutput(): PipeChannels {
        return pipeOutput
    }

    override fun resize(width: Int, height: Int) {

    }

    override fun release() {
    }
}