package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager.minecraft
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

object TestShaderPipelines {
    private val sceneCopyTextures = SimpleTextures().apply {
        addTexture(SupplierTexture { minecraft.mainRenderTarget.colorTextureId })
    }

    private val sceneCopyPipe = ExternalTextureShaderPipe(
        sceneCopyTextures,
        Supplier { -1 }
    )

    val blackHoleDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_black_hole_distortion"
        )
    ).apply {
        enableBlend = false

        val input = SimpleShaderPipe(
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
}
