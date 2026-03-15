package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import kotlin.math.max
import kotlin.math.sqrt

@CooAutoRegister
class TestTexturedBeamEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    ScreenGlowContextProvider,
    RenderEntityRenderer<TestTexturedBeamEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        private val GLOW_SAMPLE_OFFSETS = floatArrayOf(-0.36f, 0.0f, 0.36f)

        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_textured_beam"
        )

        private val quadBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUV(
                    Vector3f(-0.5f, -0.5f, 0f),
                    Vector3f(0.5f, -0.5f, 0f),
                    Vector3f(0.5f, 0.5f, 0f),
                    Vector3f(-0.5f, 0.5f, 0f)
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }

        private val beamShader = ShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/beam.fsh")
            .build()

        private val beamTextures = SimpleTextures().apply {
            addTexture(
                IdentifierTexture(
                    ResourceLocation.fromNamespaceAndPath(
                        CooParticlesConstants.MOD_ID,
                        "item/test_style.png"
                    )
                )
            )
        }

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            quadBuffer.init()
            beamShader.init()
            beamTextures.init()
        }
    }

    @field:CodecField
    var width: Float = 0.6f

    @field:CodecField
    var height: Float = 3.0f

    @field:CodecField
    var beamColor: Vector4f = Vector4f(0.5f, 0.9f, 1.0f, 1.0f)

    private val size = Vector2f()
    private val renderColor = Vector4f()
    private val glowColor = Vector3f()
    private val glowSamplePos = Vector3f()
    private val glowOffset = Vector3f()
    private val glowAxis = Vector3f()

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestTexturedBeamEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<TestTexturedBeamEntity>) {
        val glowContext = createGlowContext(input)
        val blend = DistanceAdaptiveGlow.computeBlend(
            worldPosition = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            worldRadius = effectRadius(),
            context = glowContext
        )
        if (blend.directWeight <= 1.0e-3f) {
            return
        }

        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE)
        RenderSystem.depthMask(false)
        try {
            beamShader.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                size.set(width, height)
                setFloat2("size", size)
                setFloat4("color", renderColor.set(beamColor).mul(blend.directWeight))
                setFloat("time", getTime(input.tickDelta))
                setInt("beamTex", 0)
                beamTextures.drawWith {
                    quadBuffer.draw()
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            RenderSystem.disableCull()
        }
    }

    override fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>) {
        val blend = DistanceAdaptiveGlow.computeBlend(
            worldPosition = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            worldRadius = effectRadius(),
            context = context
        )
        if (blend.screenGlowWeight <= 1.0e-3f) {
            return
        }

        glowAxis.set(0.0f, 1.0f, 0.0f)
        context.inverseViewRotationMatrix.transform(glowAxis).normalize()
        glowColor.set(beamColor.x, beamColor.y, beamColor.z)
        val sampleIntensity = beamColor.w * (1.6f * blend.screenGlowWeight) / GLOW_SAMPLE_OFFSETS.size
        val sampleRadius = max(width * 0.95f, height * 0.12f)

        for (offset in GLOW_SAMPLE_OFFSETS) {
            glowOffset.set(glowAxis).mul(height * offset)
            glowSamplePos.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()).add(glowOffset)
            output.add(
                ScreenGlow(
                    position = Vector3f(glowSamplePos),
                    color = Vector3f(glowColor),
                    radius = sampleRadius,
                    intensity = sampleIntensity,
                    softness = 0.52f
                )
            )
        }
    }

    private fun effectRadius(): Float {
        return sqrt(width * width + height * height) * 0.5f
    }

    private fun createGlowContext(input: LocalRenderInput<TestTexturedBeamEntity>): ScreenGlowRenderContext {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        return ScreenGlowRenderContext(
            tickDelta = input.tickDelta,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            viewMatrix = Matrix4f(input.viewMatrix),
            viewRotationMatrix = Matrix3f(input.viewMatrix),
            inverseViewRotationMatrix = Matrix3f(input.viewMatrix).invert(),
            projMatrix = Matrix4f(input.projMatrix),
            screenSize = Vector2f(
                minecraft.mainRenderTarget.width.toFloat(),
                minecraft.mainRenderTarget.height.toFloat()
            )
        )
    }
}
