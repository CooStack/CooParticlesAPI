package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager.minecraft
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.ShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.PingPongShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33

object ShaderPipeManagers {
    val default = ShaderPipeManager(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "default")) {
        valueOutput(ShaderPipes.simpleScreenOutput {
            minecraft.mainRenderTarget.depthTextureId
        })
        it.link(valueOutput!!, 0, valueInputPipe!!, 0)
    }
    val simpleBloom =
        ShaderPipeManager(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "simple_bloom")) {
        }.addBloomEffect(10, 1.5f)

    fun init() {
        ClientRenderPipelineManager.register(default)
        ClientRenderPipelineManager.register(simpleBloom)
    }


    private fun ShaderPipeManager.addBloomEffect(
        blurIterations: Int = 2,
        bloomIntensity: Float = 1.0f
    ): ShaderPipeManager {
        // 添加亮部提取管道
        valueInput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/bright.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 2, GL33.GL_NEAREST_MIPMAP_LINEAR
            ).addRenderHandler {
                it.setFloat("threshold", bloomIntensity)
            }.useMipmap()
        )
        val blur = addPipe(
            PingPongShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "core/bloom/blur.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ), IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "core/bloom/blur.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                { minecraft.mainRenderTarget.depthTextureId }, 1, blurIterations
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setBoolean("horizontal", false)
            }
        )

        /**
         * 这个通道需要mipmap
         * 但是他的数据来源是上一级
         * 所以要给上一级（也就是输入通道） 设置mipmap
         */
        var lastTent: ShaderPipe? = null
        for (i in 0..3) {
            val tent = addPipe(
                SimpleShaderPipe(
                    IdentifierShader(
                        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/tent.fsh"),
                        GlShaderType.FRAGMENT
                    ), { minecraft.mainRenderTarget.depthTextureId }, 1, GL33.GL_NEAREST_MIPMAP_LINEAR
                ).addRenderHandler { program ->
//                program.setInt("image", 0)
                    program.setInt("scene", 0)
                    program.setFloat("lod", 4f - i)
                }.useMipmap()
            )
            if (lastTent == null) {
                lastTent = tent
                linker.link(tent, 0, valueInputPipe!!, 1)
                continue
            }
            linker.link(tent, 0, lastTent, 0)
            lastTent = tent
        }
        linker.link(blur, 0, lastTent!!, 0)
        val kawase = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/kawase_blur.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 1, GL33.GL_LINEAR_MIPMAP_LINEAR
            ).addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("uOffset", 3f)
                program.setFloat("intensity", 2f)
            }.useMipmap()
        )
        linker.link(kawase, 0, blur, 0)  // blur的输出 提交给 kawase的输入
        // 添加Bloom混合管道
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/bloom.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 1
            ).addRenderHandler { program ->
                program.setInt("scene", 0)
                program.setInt("bloomBlur", 1)
                program.setFloat("intensity", bloomIntensity)
            }
        )


        linker.link(valueOutput!!, 0, valueInputPipe!!, 0)
        linker.link(valueOutput!!, 1, kawase, 0)
        return this
    }
}