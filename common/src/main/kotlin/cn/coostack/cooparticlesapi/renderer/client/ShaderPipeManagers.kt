package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager.minecraft
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.MCHookedShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.PingPongShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.SimpleShaderPipe
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33
import java.util.function.Supplier

object ShaderPipeManagers {
    val default = ShaderPipeManager(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "default"))
    val testSubChannel = ShaderPipeManager(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test"))
        .beforeInit {
            addPipe(
                SimpleShaderPipe(
                    IdentifierShader(
                        ResourceLocation.fromNamespaceAndPath(
                            CooParticlesConstants.MOD_ID,
                            "test/frag/two_channel.fsh"
                        ),
                        GlShaderType.FRAGMENT
                    ), {
                        minecraft.mainRenderTarget.depthTextureId
                    }, 2
                )
            )
                .addPipe(
                    SimpleShaderPipe(
                        IdentifierShader(
                            ResourceLocation.fromNamespaceAndPath(
                                CooParticlesConstants.MOD_ID,
                                "test/frag/mix_two_channel.fsh"
                            ),
                            GlShaderType.FRAGMENT
                        ), {
                            minecraft.mainRenderTarget.depthTextureId
                        }, 2
                    ).addRenderHandler {
                        it.setInt("scene", 0)
                        it.setInt("back_scene", 1)
                    }
                )
        }

    val testBloom = ShaderPipeManager(ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_bloom"))
        .beforeInit {
            addBloomEffect(10, 1.5f)
        }

    fun init() {
        ClientRenderPipelineManager.register(default)
        ClientRenderPipelineManager.register(testSubChannel)
        ClientRenderPipelineManager.register(testBloom)
    }

    private fun ShaderPipeManager.addBloomEffect(
        blurIterations: Int = 2,
        bloomIntensity: Float = 1.0f
    ): ShaderPipeManager {
        // 添加亮部提取管道
        addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/bright.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 2
            ).addRenderHandler {
                it.setFloat("threshold", bloomIntensity)
            }
        )
        addPipe(
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
                { minecraft.mainRenderTarget.depthTextureId }, 2, blurIterations
            ).addRenderHandlerPong { program ->
                program.setInt("image", 0)
                program.setInt("bright", 1)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("image", 0)
                program.setInt("bright", 1)
                program.setBoolean("horizontal", false)
            }
        )
        addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/kawase_blur.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 2
            ).addRenderHandler { program ->
                program.setInt("image", 0)
                program.setInt("bright", 1)
                program.setFloat("uOffset", 5f)
                program.setFloat("intensity", bloomIntensity)
            }
        )
        // 添加Bloom混合管道
        addPipe(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/bloom.fsh"),
                    GlShaderType.FRAGMENT
                ), { minecraft.mainRenderTarget.depthTextureId }, 2
            ).addRenderHandler { program ->
                program.setInt("image", 0)
                program.setInt("bloomBlur", 1)
                program.setFloat("intensity", bloomIntensity)
            }
        )
        return this
    }
}