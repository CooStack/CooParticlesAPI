package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.LineGlowSampling
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import kotlin.math.max

class TestHybridGlowPipeEntity(world: Level?) : RenderEntity(world), ScreenGlowContextProvider {
    companion object {
        private const val GLOW_SAMPLE_COUNT = 5
        private const val DIRECT_FADE_START_PX = 22.0f
        private const val DIRECT_FADE_END_PX = 6.0f

        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_hybrid_glow_pipe"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestHybridGlowPipeEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.width)
                buf.writeFloat(entity.height)
                buf.writeFloat(entity.glowStrength)
                buf.writeFloat(entity.softness)
                buf.writeFloat(entity.pipeColor.x)
                buf.writeFloat(entity.pipeColor.y)
                buf.writeFloat(entity.pipeColor.z)
                buf.writeFloat(entity.pipeColor.w)
            },
            { buf, entity ->
                entity.width = buf.readFloat()
                entity.height = buf.readFloat()
                entity.glowStrength = buf.readFloat()
                entity.softness = buf.readFloat()
                entity.pipeColor = Vector4f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
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

        private val pipeShader = ShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/hybrid_glow_pipe.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            quadBuffer.init()
            pipeShader.init()
        }

        fun reloadStaticResources() {
            quadBuffer.release()
            pipeShader.release()
            initialized = false
        }
    }

    var width by tracked(0.52f)
    var height by tracked(9.0f)
    var glowStrength by tracked(2.7f)
    var softness by tracked(0.60f)
    var pipeColor by tracked(Vector4f(0.35f, 0.84f, 1.0f, 1.0f))

    private val size = Vector2f()
    private val renderColor = Vector4f()
    private val glowAxis = Vector3f()
    private val glowColor = Vector3f()
    private val worldPosition = Vector3f()

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun release() {
    }

    override fun render(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float
    ) {
        val context = createGlowContext(tickDelta, viewMatrix, projMatrix)
        val blend = computeBlend(context)
        if (blend.directWeight <= 1.0e-3f) {
            return
        }

        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE)
        RenderSystem.depthMask(false)
        pipeShader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            size.set(width, height)
            setFloat2("size", size)
            setFloat("time", getTime(tickDelta))
            renderColor.set(pipeColor).mul(1.0f, 1.0f, 1.0f, blend.directWeight)
            setFloat4("color", renderColor)
            quadBuffer.draw()
        }
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    override fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>) {
        val blend = computeBlend(context)
        if (blend.screenGlowWeight <= 1.0e-3f) {
            return
        }
        val compensation = DistanceAdaptiveGlow.farGlowCompensationFromProjectedRadiusPx(blend.projectedRadiusPx)

        glowAxis.set(0.0f, 1.0f, 0.0f)
        context.inverseViewRotationMatrix.transform(glowAxis).normalize()
        glowColor.set(pipeColor.x, pipeColor.y, pipeColor.z)
        val sampleRadius = max(width * 1.65f, height * 0.16f) * compensation.radiusScale
        val sampleIntensity = glowStrength * pipeColor.w * blend.screenGlowWeight * compensation.intensityScale
        LineGlowSampling.createSamples(
            center = worldPosition.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
            axis = glowAxis,
            halfLength = height * (0.46f + compensation.persistence * 0.06f),
            baseRadius = sampleRadius,
            totalIntensity = sampleIntensity,
            sampleCount = GLOW_SAMPLE_COUNT
        ).forEach { sample ->
            output.add(
                ScreenGlow(
                    position = Vector3f(sample.position),
                    color = Vector3f(glowColor),
                    radius = sample.radius,
                    intensity = sample.intensity,
                    softness = softness
                )
            )
        }
    }

    private fun transitionRadius(): Float {
        return max(width * 0.58f, 0.08f)
    }

    private fun computeBlend(context: ScreenGlowRenderContext) = DistanceAdaptiveGlow.computeBlend(
        worldPosition = worldPosition.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
        worldRadius = transitionRadius(),
        context = context,
        directFadeStartPx = DIRECT_FADE_START_PX,
        directFadeEndPx = DIRECT_FADE_END_PX
    )

    private fun createGlowContext(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f
    ): ScreenGlowRenderContext {
        val minecraft = Minecraft.getInstance()
        val camera = minecraft.gameRenderer.mainCamera.position
        return ScreenGlowRenderContext(
            tickDelta = tickDelta,
            cameraWorldPos = Vector3f(camera.x.toFloat(), camera.y.toFloat(), camera.z.toFloat()),
            viewMatrix = Matrix4f(viewMatrix),
            viewRotationMatrix = Matrix3f(viewMatrix),
            inverseViewRotationMatrix = Matrix3f(viewMatrix).invert(),
            projMatrix = Matrix4f(projMatrix),
            screenSize = Vector2f(
                minecraft.mainRenderTarget.width.toFloat(),
                minecraft.mainRenderTarget.height.toFloat()
            )
        )
    }
}
