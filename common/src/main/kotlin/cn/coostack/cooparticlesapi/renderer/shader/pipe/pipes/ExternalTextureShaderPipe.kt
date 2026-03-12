package cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.PipeChannels
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.handler.ShaderProgramUploader
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTextures
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.glsl.SimpleFrameBuffer
import cn.coostack.cooparticlesapi.renderer.shader.vertex.VertexBuffers
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33
import java.util.function.Supplier

class ExternalTextureShaderPipe(
    private val textures: GlTextures,
    depthSupplier: Supplier<Int>,
    colorChannelCount: Int = textures.getTextureCounts(),
    private val textureFilterMod: Int = GL33.GL_LINEAR
) : ShaderPipe {
    private val screenVertex = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/vertexes/screen.vsh"),
        GlShaderType.VERTEX
    )
    private val screenFragment = IdentifierShader(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
        GlShaderType.FRAGMENT
    )
    private val shaderVertexes = VertexBuffers.getScreenBuffer()
    private val handles = ArrayList<ShaderProgramUploader>()
    private val screenProgram = ShaderProgramBuilder()
        .vertex(screenVertex)
        .fragment(screenFragment)
        .build()
    private val fbo = SimpleFrameBuffer(colorChannelCount, depthSupplier)

    override fun init() {
        fbo.setTextureFilterMod(textureFilterMod)
        textures.init()
        screenProgram.init()
        fbo.init()
        shaderVertexes.init()
    }

    fun capture() {
        screenProgram.useOnContext {
            handles.forEach {
                it.uploadShaderData(this)
            }
            fbo.writeFrameBufferWith {
                RenderSystem.disableBlend()
                textures.drawWith {
                    shaderVertexes.draw()
                }
            }
        }
    }

    override fun addRenderHandler(handler: ShaderProgramUploader): ShaderPipe {
        handles.add(handler)
        return this
    }

    override fun fbo(): GlFrameBuffer {
        return fbo
    }

    override fun useMipmap(): ShaderPipe {
        fbo.useMipmap()
        return this
    }

    override fun write(invoker: ShaderPipe.() -> Unit) {
        capture()
    }

    override fun writeFromChannel(channel: PipeChannels): ShaderPipe {
        screenProgram.useOnContext {
            handles.forEach {
                it.uploadShaderData(this)
            }
            fbo.writeFrameBufferWith {
                RenderSystem.disableBlend()
                channel.useOnContext {
                    shaderVertexes.draw()
                }
            }
        }
        return this
    }

    override fun drawPipeFrame() {
        screenProgram.useOnContext {
            handles.forEach {
                it.uploadShaderData(this)
            }
            fbo.readFrameBufferWith {
                shaderVertexes.draw()
            }
        }
    }

    override fun getFrameOutput(): PipeChannels {
        return fbo.outputChannels()
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
