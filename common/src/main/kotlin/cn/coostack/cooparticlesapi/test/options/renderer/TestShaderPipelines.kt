package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager.minecraft
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.PingPongShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33
import java.util.function.Supplier

object TestShaderPipelines {
    private fun createSceneCopyPipe(): ExternalTextureShaderPipe {
        val sceneCopyTextures = SimpleTextures().apply {
            addTexture(SupplierTexture { minecraft.mainRenderTarget.colorTextureId })
        }
        return ExternalTextureShaderPipe(sceneCopyTextures, Supplier { -1 })
    }

    private fun createDistortionInputPipe(): SimpleShaderPipe {
        return SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(
                    CooParticlesConstants.MOD_ID,
                    "pipe/frags/screen.fsh"
                ),
                GlShaderType.FRAGMENT
            ),
            Supplier { minecraft.mainRenderTarget.depthTextureId },
            2
        )
    }

    val blackHoleDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_black_hole_distortion"
        )
    ).apply {
        enableBlend = false

        val input = createDistortionInputPipe()
        val sceneCopyPipe = createSceneCopyPipe()
        valueInput(input)
        val sceneCopy = addPipe(sceneCopyPipe)
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "test/frag/black_hole_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).addRenderHandler { program ->
                program.setInt("holeMask", 0)
                program.setInt("distortionMask", 1)
                program.setInt("sceneTex", 2)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(input, 0).to(valueOutput!!, 0)
            linker.from(input, 1).to(valueOutput!!, 1)
            linker.from(sceneCopy, 0).to(valueOutput!!, 2)
        }
    }

    val glowSphereDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_glow_sphere_distortion"
        )
    ).apply {
        enableBlend = false

        val input = createDistortionInputPipe()
        val sceneCopyPipe = createSceneCopyPipe()
        valueInput(input)
        val sceneCopy = addPipe(sceneCopyPipe)
        val glowBlur = addPipe(
            PingPongShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "core/bloom/blur.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 },
                1,
                4,
                GL33.GL_LINEAR
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", 7.5f)
                program.setFloat("range", 4.2f)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", 7.5f)
                program.setFloat("range", 4.2f)
                program.setBoolean("horizontal", false)
            }
        )
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "test/frag/glow_sphere_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).addRenderHandler { program ->
                program.setInt("glowMask", 0)
                program.setInt("distortionMask", 1)
                program.setInt("sceneTex", 2)
                program.setInt("blurredGlow", 3)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(input, 0).to(valueOutput!!, 0)
            linker.from(input, 1).to(valueOutput!!, 1)
            linker.from(sceneCopy, 0).to(valueOutput!!, 2)
            linker.from(input, 0).to(glowBlur, 0)
            linker.from(glowBlur, 0).to(valueOutput!!, 3)
        }
    }
}
