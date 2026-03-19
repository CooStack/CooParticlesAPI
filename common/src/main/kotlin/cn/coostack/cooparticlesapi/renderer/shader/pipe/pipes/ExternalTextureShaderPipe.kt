package cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
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
import org.lwjgl.opengl.GL33.GL_CULL_FACE
import org.lwjgl.opengl.GL33.GL_DEPTH_TEST
import org.lwjgl.opengl.GL33.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL33.glDisable
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glIsEnabled
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
    private val screenProgram = AdvancedShaderProgramBuilder()
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
            setInt("tex", 0)
            handles.forEach {
                it.uploadShaderData(this)
            }
            fbo.writeFrameBufferWith {
                val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
                val cullEnabled = glIsEnabled(GL_CULL_FACE)
                val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
                RenderSystem.disableBlend()
                RenderSystem.disableDepthTest()
                RenderSystem.disableCull()
                if (scissorEnabled) {
                    glDisable(GL_SCISSOR_TEST)
                }
                try {
                    textures.drawWith {
                        shaderVertexes.draw()
                    }
                } finally {
                    if (depthEnabled) {
                        RenderSystem.enableDepthTest()
                    }
                    if (cullEnabled) {
                        RenderSystem.enableCull()
                    }
                    if (scissorEnabled) {
                        glEnable(GL_SCISSOR_TEST)
                    }
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
            setInt("tex", 0)
            handles.forEach {
                it.uploadShaderData(this)
            }
            fbo.writeFrameBufferWith {
                val depthEnabled = glIsEnabled(GL_DEPTH_TEST)
                val cullEnabled = glIsEnabled(GL_CULL_FACE)
                val scissorEnabled = glIsEnabled(GL_SCISSOR_TEST)
                RenderSystem.disableBlend()
                RenderSystem.disableDepthTest()
                RenderSystem.disableCull()
                if (scissorEnabled) {
                    glDisable(GL_SCISSOR_TEST)
                }
                try {
                    channel.useOnContext {
                        shaderVertexes.draw()
                    }
                } finally {
                    if (depthEnabled) {
                        RenderSystem.enableDepthTest()
                    }
                    if (cullEnabled) {
                        RenderSystem.enableCull()
                    }
                    if (scissorEnabled) {
                        glEnable(GL_SCISSOR_TEST)
                    }
                }
            }
        }
        return this
    }

    override fun drawPipeFrame() {
        screenProgram.useOnContext {
            setInt("tex", 0)
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
