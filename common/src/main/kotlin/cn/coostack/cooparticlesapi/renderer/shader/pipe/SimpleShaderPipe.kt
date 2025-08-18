package cn.coostack.cooparticlesapi.renderer.shader.pipe

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

/**
 * 如果上一个pipe 提供了多个通道
 * 那么下一个pipe 想要使用这些通道就必须设置>1
 * 也就是说, 上一个pipe设置了多少 下一个pipe也要设置多少
 * @param colorChannelCount 颜色组件通道, 如果输入大于1 则需要手动设置uniform (gl要求)
 * @param depthSupplier 如果让他自己生成 则输入-1
 * GLSL 获取屏幕uv 使用 in vec2 screen_uv
 */
class SimpleShaderPipe(
    val fragment: GlShader, depthSupplier: Supplier<Int>, colorChannelCount: Int = 1,
) :
    ShaderPipe {
    var shareDepth = true
    private val screenVertex = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh"),
        GlShaderType.VERTEX
    )
    private val shaderVertexes = VertexBuffers.getScreenBuffer()

    private val handles = ArrayList<ShaderProgramUploader>()
    private var count = 1

    private val screenProgram = ShaderProgramBuilder()
        .vertex(screenVertex)
        .fragment(fragment)
        .build()


    val width: Int
        get() = Minecraft.getInstance().mainRenderTarget.width
    val height: Int
        get() = Minecraft.getInstance().mainRenderTarget.height
    private val fbo = SimpleFrameBuffer(colorChannelCount, depthSupplier)

    override fun init() {
        require(fragment.type == GlShaderType.FRAGMENT)
        screenProgram.init()
        fbo.init()
        shaderVertexes.init()
    }

    override fun setPipeRenderCount(count: Int): ShaderPipe {
        this.count = count.coerceAtLeast(1)
        return this
    }

    override fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe {
        handles.add(handler)
        return this
    }

    override fun fbo(): GlFrameBuffer {
        return fbo
    }

    override fun shareDepth(): Boolean {
        return shareDepth
    }

    override fun textureFilterMod(mod: Int): ShaderPipe {
        fbo.setTextureFilterMod(mod)
        return this
    }

    override fun write(invoker: ShaderPipe.() -> Unit) {
        // 向fbo写入内容
        fbo.writeFrameBufferWith {
            RenderSystem.disableBlend()
            invoker()
        }
    }

    override fun drawPipeFrame() {
        drawOnce()
    }


    private fun drawOnce() {
        screenProgram.useOnContext {
            for (handler in handles) {
                handler.uploadShaderData(screenProgram)
            }
            fbo.readFrameBufferWith {
                // 绘制屏幕四边形
                shaderVertexes.draw()
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        fbo.resize(width, height)
    }

    override fun release() {
        fbo.release()
        screenProgram.release()
        shaderVertexes.release()
    }
}