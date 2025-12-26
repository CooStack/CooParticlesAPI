package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager.minecraft
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.PingPongShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.TextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.ReferenceTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33

/**
 * 实现一个真正符合光学的泛光
 * 其实是要在渲染区块方块之前 （渲染天空之后）
 * 进行着色， 然后再进行直接泛光处理
 *
 * 然后渲染区块， 在对区块内容进行后处理 （光源）
 * 最后再渲染其他
 * 但是因为IRIS逆天的兼容性， 导致我的渲染始终只能在最后渲染
 * 因此这个光效一定是有问题的
 *
 * 同时也不太可能对世界进行后处理了
 *
 * 不过目前框架是无法实现对mc的内容进行后处理的 （也许直接套用mc的color texture channel能解决？）
 *
 */
object ShaderPipeManagers {
    val default = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "default"
        )
    ).setLinkerFunc {
        valueOutput(ShaderPipes.simpleScreenOutput {
            minecraft.mainRenderTarget.depthTextureId
        })
        it.link(valueOutput!!, 0, valueInputPipe!!, 0)
    }
    val simpleBloom =
        ShaderPipeManager(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID,
                "simple_bloom"
            )
        ).setLinkerFunc {
        }.addBloomEffect(
            10,
            1.5f,
            30f,
            10f,
            5f
        )

    fun init() {
        ClientRenderPipelineManager.register(default)
        ClientRenderPipelineManager.register(simpleBloom)
    }

    private fun ShaderPipeManager.addBloomEffect(
        blurIterations: Int = 2,
        bloomIntensity: Float = 1.0f,
        gaussSigma: Float = 2f,
        gaussRange: Float = 2f,
        lodLevel: Float = 15f
    ): ShaderPipeManager {
        // 添加亮部 提取管道
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
                ),
                { minecraft.mainRenderTarget.depthTextureId }, 1, blurIterations,
                GL33.GL_LINEAR
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", gaussSigma)
                program.setFloat("range", gaussRange)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", gaussSigma)
                program.setFloat("range", gaussRange)
                program.setBoolean("horizontal", false)
            }
        )
        val tent = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/tent.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 1, GL33.GL_NEAREST_MIPMAP_LINEAR
            ).addRenderHandler { program ->
                program.setInt("scene", 0)
                program.setFloat("lod", lodLevel)
            }.useMipmap()
        )
        val accumulate = addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "core/bloom/accumulate.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }
            ).addRenderHandler { program ->
                program.setFloat("intensity", bloomIntensity)
                program.setInt("levels", lodLevel.toInt())
            }
        )
        linker.link(tent, 0, valueInputPipe!!, 1)
        linker.link(accumulate, 0, tent, 0)
        linker.link(blur, 0, accumulate, 0)
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
                program.setInt("sceneDepth", 2)
                program.setFloat("intensity", bloomIntensity)
            }
        )

        linker.link(valueOutput!!, 0, valueInputPipe!!, 0)
        linker.link(valueOutput!!, 1, blur, 0)
//        linker.link( 如果有一个自定义材质纹理要作为pipe输入 则使用这个 bloom不需要多余的材质纹理 所以这里注释
//            valueOutput!!, 2, addPipe(
//                TextureShaderPipe(SimpleTextures().apply {
//                    addTexture(ReferenceTexture(minecraft.mainRenderTarget.depthTextureId))
//                })
//            ), 0
//        )
        return this
    }
}