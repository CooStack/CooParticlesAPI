package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.api.pipe.from
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.ExternalTextureShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.OutputDepthPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.PingPongShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.pipe.pipes.SimpleShaderPipe
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.texture.SupplierTexture
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

internal object TestRelativisticShaderPipelines {
    private const val ACCRETION_BLUR_PASSES = 8
    private const val ACCRETION_BLUR_SIGMA = 20.0f
    private const val ACCRETION_BLUR_RANGE = 14.0f
    private const val GLOW_SPHERE_BLUR_PASSES = 8
    private const val GLOW_SPHERE_BLUR_SIGMA = 4.8f
    private const val GLOW_SPHERE_BLUR_RANGE = 4.4f

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    private var initialized = false
    private var cachedWidth = -1
    private var cachedHeight = -1

    private fun createSceneCopyPipe(): ExternalTextureShaderPipe {
        val sceneCopyTextures = SimpleTextures().apply {
            addTexture(SupplierTexture { minecraft.mainRenderTarget.colorTextureId })
        }
        return ExternalTextureShaderPipe(sceneCopyTextures, Supplier { -1 })
    }

    private fun createBlackHoleInputPipe(): SimpleShaderPipe {
        return SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                GlShaderType.FRAGMENT
            ),
            Supplier { minecraft.mainRenderTarget.depthTextureId },
            2
        )
    }

    private fun createGlowSphereInputPipe(): SimpleShaderPipe {
        return SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                GlShaderType.FRAGMENT
            ),
            Supplier { minecraft.mainRenderTarget.depthTextureId },
            2
        )
    }

    private fun createAccretionInputPipe(): SimpleShaderPipe {
        return SimpleShaderPipe(
            IdentifierShader(
                ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "pipe/frags/screen.fsh"),
                GlShaderType.FRAGMENT
            ),
            Supplier { minecraft.mainRenderTarget.depthTextureId },
            3
        )
    }

    private val blackHoleDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_black_hole_distortion")
    ).apply {
        enableBlend = false
        val input = createBlackHoleInputPipe()
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

    private val glowSphereDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_glow_sphere_distortion")
    ).apply {
        enableBlend = false

        val input = createGlowSphereInputPipe()
        val sceneCopyPipe = createSceneCopyPipe()
        val sceneDepthPipe = addPipe(OutputDepthPipe(Supplier { minecraft.mainRenderTarget.depthTextureId }))
        val glowBlur = addPipe(
            PingPongShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/blur.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 },
                1,
                GLOW_SPHERE_BLUR_PASSES
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", GLOW_SPHERE_BLUR_SIGMA)
                program.setFloat("range", GLOW_SPHERE_BLUR_RANGE)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", GLOW_SPHERE_BLUR_SIGMA)
                program.setFloat("range", GLOW_SPHERE_BLUR_RANGE)
                program.setBoolean("horizontal", false)
            }
        )

        valueInput(input)
        val sceneCopy = addPipe(sceneCopyPipe)
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
                program.setInt("sceneDepth", 4)
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
            linker.from(sceneDepthPipe, 0).to(valueOutput!!, 4)
        }
    }

    private val accretionDiskDistortion = ShaderPipeManager(
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_accretion_disk_distortion")
    ).apply {
        enableBlend = false

        val input = createAccretionInputPipe()
        val sceneCopyPipe = createSceneCopyPipe()
        val sceneDepthPipe = addPipe(OutputDepthPipe(Supplier { minecraft.mainRenderTarget.depthTextureId }))
        val glowBlur = addPipe(
            PingPongShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "core/bloom/blur.fsh"),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 },
                1,
                ACCRETION_BLUR_PASSES
            ).addRenderHandlerPong { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", ACCRETION_BLUR_SIGMA)
                program.setFloat("range", ACCRETION_BLUR_RANGE)
                program.setBoolean("horizontal", true)
            }.addRenderHandler { program ->
                program.setInt("bright", 0)
                program.setFloat("sigma", ACCRETION_BLUR_SIGMA)
                program.setFloat("range", ACCRETION_BLUR_RANGE)
                program.setBoolean("horizontal", false)
            }
        )

        valueInput(input)
        val sceneCopy = addPipe(sceneCopyPipe)
        valueOutput(
            SimpleShaderPipe(
                IdentifierShader(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "test/frag/accretion_disk_composite.fsh"
                    ),
                    GlShaderType.FRAGMENT
                ),
                Supplier { -1 }
            ).addRenderHandler { program ->
                program.setInt("glowMask", 0)
                program.setInt("distortionMask", 1)
                program.setInt("sceneLightMask", 2)
                program.setInt("sceneTex", 3)
                program.setInt("blurredGlow", 4)
                program.setInt("sceneDepth", 5)
            }
        )

        beforeRender {
            sceneCopyPipe.capture()
        }
        setLinkerFunc { linker ->
            linker.from(input, 0).to(valueOutput!!, 0)
            linker.from(input, 1).to(valueOutput!!, 1)
            linker.from(input, 2).to(valueOutput!!, 2)
            linker.from(sceneCopy, 0).to(valueOutput!!, 3)
            linker.from(input, 0).to(glowBlur, 0)
            linker.from(glowBlur, 0).to(valueOutput!!, 4)
            linker.from(sceneDepthPipe, 0).to(valueOutput!!, 5)
        }
    }

    fun renderBlackHole(drawMask: () -> Unit) {
        ensureReady()
        if (!initialized) {
            return
        }
        blackHoleDistortion.writeFrame(Runnable { drawMask() })
        minecraft.mainRenderTarget.bindWrite(false)
        blackHoleDistortion.render()
    }

    fun renderGlowSphere(drawMask: () -> Unit) {
        ensureReady()
        if (!initialized) {
            return
        }
        glowSphereDistortion.writeFrame(Runnable { drawMask() })
        minecraft.mainRenderTarget.bindWrite(false)
        glowSphereDistortion.render()
    }

    fun renderAccretionDisk(drawMask: () -> Unit) {
        ensureReady()
        if (!initialized) {
            return
        }
        accretionDiskDistortion.writeFrame(Runnable { drawMask() })
        minecraft.mainRenderTarget.bindWrite(false)
        accretionDiskDistortion.render()
    }

    private fun ensureReady() {
        val width = minecraft.mainRenderTarget.width
        val height = minecraft.mainRenderTarget.height
        if (width <= 0 || height <= 0) {
            return
        }

        if (!initialized) {
            blackHoleDistortion.init()
            glowSphereDistortion.init()
            accretionDiskDistortion.init()
            initialized = true
            cachedWidth = width
            cachedHeight = height
            return
        }

        if (width != cachedWidth || height != cachedHeight) {
            blackHoleDistortion.resize(width, height)
            glowSphereDistortion.resize(width, height)
            accretionDiskDistortion.resize(width, height)
            cachedWidth = width
            cachedHeight = height
        }
    }
}
