package cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

class PingPongShaderPipe(
    val fragmentPing: GlShader,
    val fragmentPong: GlShader,
    depthSupplier: Supplier<Int>,
    colorChannelCount: Int = 1,
    private var pingpongCount: Int = 2,  // 外部指定循环次数
) : ShaderPipe {

    var shareDepth = true
    private val screenVertex = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh"),
        GlShaderType.VERTEX
    )
    private val vertexes = VertexBuffers.getScreenBuffer()

    private val handles = ArrayList<ShaderProgramUploader>()
    private val pongHandles = ArrayList<ShaderProgramUploader>()

    private val screenPingProgram = ShaderProgramBuilder()
        .vertex(screenVertex)
        .fragment(fragmentPing)
        .build()

    private val screenPongProgram = ShaderProgramBuilder()
        .vertex(screenVertex)
        .fragment(fragmentPong)
        .build()
    private val pingFBO = SimpleFrameBuffer(colorChannelCount, depthSupplier)
    private val pongFBO = SimpleFrameBuffer(colorChannelCount, depthSupplier)

    private var ping = true
    val width: Int
        get() = Minecraft.getInstance().mainRenderTarget.width
    val height: Int
        get() = Minecraft.getInstance().mainRenderTarget.height

    override fun init() {
        require(fragmentPing.type == GlShaderType.FRAGMENT)
        require(fragmentPong.type == GlShaderType.FRAGMENT)
        screenPingProgram.init()
        screenPongProgram.init()
        pingFBO.init()
        pongFBO.init()
        vertexes.init()
    }

    override fun addRenderHandler(handler: ShaderProgramUploader): PingPongShaderPipe {
        handles.add(handler)
        return this
    }

    fun addRenderHandlerPong(handler: ShaderProgramUploader): PingPongShaderPipe {
        pongHandles.add(handler)
        return this
    }

    override fun fbo(): GlFrameBuffer {
        return if (ping) pingFBO else pongFBO  // 默认返回 A，可以根据需要改
    }


    override fun useMipmap(): ShaderPipe {
        pingFBO.useMipmap()
        pongFBO.useMipmap()
        return this
    }

    override fun write(invoker: ShaderPipe.() -> Unit) {
        pingFBO.writeFrameBufferWith {
            RenderSystem.disableBlend()
            invoker()
        }
    }

    override fun drawPipeFrame() {
        // Ping-Pong 循环渲染
        drawPingPong()

        // 结果向外绘制
        val current = getCurrentShader()
        current.useOnContext {
            for (handler in getCurrentHandler()) {
                handler.uploadShaderData(current)
            }
            fbo().readFrameBufferWith {
                vertexes.draw()
            }
        }
        // 重置
        ping = true
    }

    override fun getFrameOutput(): PipeChannels {
        return fbo().outputChannels()
    }

    override fun writeFromChannel(channel: PipeChannels): PingPongShaderPipe {
        channel.drawWith {
            val current = getCurrentShader()
            val currentHandlers = getCurrentHandler()
            // 绑定当前的片段着色器
            current.useOnContext {
                // 上传数据
                for (handler in currentHandlers) {
                    handler.uploadShaderData(current)
                }
                // 绘制到ping
                write {
                    vertexes.draw()
                }
                // ping pong 渲染
                // 最后输出
                drawPingPong()
            }
        }
        return this
    }

    private fun drawPingPong() {
        repeat(pingpongCount - 1) {
            val program = getCurrentShader()
            val current = fbo()
            val another = getAnother()
            another.writeFrameBufferWith {
                program.useOnContext {
                    for (handler in getCurrentHandler()) handler.uploadShaderData(program)
                    current.readFrameBufferWith {
                        vertexes.draw()
                    }
                }
            }
            ping = !ping
        }
    }

    /**
     * @param count 绘制次数 (>1 则将这个画面反复绘制)
     * 多次绘制用于节省pipe对象个数
     */
    fun setPipeRenderCount(count: Int): PingPongShaderPipe {
        this.pingpongCount = count
        return this
    }

    override fun resize(width: Int, height: Int) {
        pingFBO.resize(width, height)
        pongFBO.resize(width, height)
    }

    override fun release() {
        pingFBO.release()
        pongFBO.release()
        screenPingProgram.release()
        vertexes.release()
    }

    private fun getAnother(): GlFrameBuffer = if (ping) pongFBO else pingFBO
    private fun getCurrentShader(): CooShaderProgram = if (ping) screenPingProgram else screenPongProgram
    private fun getCurrentHandler(): List<ShaderProgramUploader> = if (ping) handles else pongHandles
}
