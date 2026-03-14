package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityInputBlendMode
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.glow.BrightSourceOrbProfile
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveOrbGlowCompensation
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
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
import kotlin.math.max
import kotlin.math.sin

class TestGlowSphereEntity(world: Level?) : RenderEntity(world), WorldLightProvider, ScreenGlowContextProvider {
    companion object {
        private const val DIRECT_FADE_START_PX = 24.0f
        private const val DIRECT_FADE_END_PX = 7.0f

        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_glow_sphere"
        )

        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestGlowSphereEntity(null) },
            { buf, entity ->
                buf.writeFloat(entity.radius)
                buf.writeFloat(entity.intensity)
                buf.writeFloat(entity.haloIntensity)
                buf.writeFloat(entity.haloRadiusScale)
                buf.writeFloat(entity.fresnelStrength)
                buf.writeFloat(entity.distanceCompensation)
                buf.writeFloat(entity.animationSpeed)
                buf.writeFloat(entity.overbrightClamp)
                buf.writeFloat(entity.glowColor.x)
                buf.writeFloat(entity.glowColor.y)
                buf.writeFloat(entity.glowColor.z)
            },
            { buf, entity ->
                entity.radius = buf.readFloat()
                entity.intensity = buf.readFloat()
                entity.haloIntensity = buf.readFloat()
                entity.haloRadiusScale = buf.readFloat()
                entity.fresnelStrength = buf.readFloat()
                entity.distanceCompensation = buf.readFloat()
                entity.animationSpeed = buf.readFloat()
                entity.overbrightClamp = buf.readFloat()
                entity.glowColor = Vector3f(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                )
            }
        )

        private val sphereBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genBall(1f, 96, 144),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val glowShader = ShaderProgramBuilder()
            .vertex("test/vtx/glow_sphere.vsh")
            .fragment("test/frag/glow_sphere.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            sphereBuffer.init()
            glowShader.init()
        }

        fun reloadStaticResources() {
            sphereBuffer.release()
            glowShader.release()
            initialized = false
        }
    }

    var radius by tracked(4.2f)
    var intensity by tracked(6.4f)
    var haloIntensity by tracked(3.2f)
    var haloRadiusScale by tracked(1.65f)
    var fresnelStrength by tracked(1.35f)
    var distanceCompensation by tracked(1.0f)
    var animationSpeed by tracked(1.0f)
    var overbrightClamp by tracked(6.4f)
    var glowColor by tracked(Vector3f(0.56f, 0.86f, 1.28f))

    private val worldPosition = Vector3f()
    private val coreGlowColor = Vector3f()
    private val haloGlowColor = Vector3f()

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun getRenderPass(): RenderEntityRenderPass {
        return RenderEntityRenderPass.POST_PROCESS
    }

    override fun getInputBlendMode(): RenderEntityInputBlendMode {
        return RenderEntityInputBlendMode.ALPHA
    }

    override fun release() {
    }

    override fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>) {
        val pulse = 0.92f + 0.08f * sin(getTime(tickDelta) * animationSpeed * 2.05f)
        output.add(
            WorldLight(
                position = Vector3f(currentWorldPosition()),
                color = Vector3f(glowColor),
                radius = radius * (5.2f + haloRadiusScale * 2.1f),
                intensity = (intensity * 0.34f + haloIntensity * 0.18f) * pulse,
                softness = 0.50f
            )
        )
    }

    override fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>) {
        val blend = computeBlend(context)
        if (blend.screenGlowWeight <= 1.0e-3f) {
            return
        }

        val compensation = computeOrbCompensation(blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)
        val haloProfile = max(
            1.0e-3f,
            DistanceAdaptiveGlow.orbScreenHaloProfileFromProjectedRadiusPx(blend.projectedRadiusPx)
        )
        val baseIntensity = ((intensity * 0.42f) + (haloIntensity * 0.46f)) * blend.screenGlowWeight
        val outerRadius = radius * haloRadiusScale * compensation.radiusScale * sourceProfile.haloSpread
        val outerIntensity =
            (baseIntensity * (0.46f + compensation.persistence * 0.16f) * compensation.intensityScale)
                .coerceAtMost(9.4f)
        val coreRadius = max(radius * (0.42f + sourceProfile.shellVisibility * 0.18f), outerRadius * 0.18f)

        coreGlowColor.set(glowColor).lerp(Vector3f(1.0f, 1.0f, 1.0f), sourceProfile.coreWhiteness)
        haloGlowColor.set(glowColor).lerp(Vector3f(1.0f, 1.0f, 1.0f), sourceProfile.coreWhiteness * 0.14f)

        output.add(
            ScreenGlow(
                position = Vector3f(currentWorldPosition()),
                color = Vector3f(coreGlowColor),
                radius = coreRadius,
                intensity = (baseIntensity * (0.92f + compensation.persistence * 0.14f)).coerceAtMost(6.2f),
                softness = (0.78f - sourceProfile.shellVisibility * 0.16f).coerceIn(0.56f, 0.82f),
                haloProfile = haloProfile
            )
        )
        output.add(
            ScreenGlow(
                position = Vector3f(currentWorldPosition()),
                color = Vector3f(haloGlowColor),
                radius = outerRadius,
                intensity = outerIntensity,
                softness = (compensation.softness + 0.12f).coerceAtMost(0.92f),
                haloProfile = haloProfile
            )
        )
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
        val compensation = computeOrbCompensation(blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)

        RenderSystem.enableCull()
        RenderSystem.depthMask(false)
        glowShader.useOnContext {
            matrices.pushMatrix()
            matrices.scale(radius, radius, radius)
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat3("color", glowColor)
            setFloat("intensity", intensity)
            setFloat("haloIntensity", haloIntensity)
            setFloat("haloRadiusScale", haloRadiusScale)
            setFloat("fresnelStrength", fresnelStrength)
            setFloat("animationSpeed", animationSpeed)
            setFloat("projectedRadiusPx", blend.projectedRadiusPx)
            setFloat("farPersistence", compensation.persistence)
            setFloat("directOpacity", blend.directWeight)
            setFloat("overbrightClamp", overbrightClamp)
            setFloat("coreWhiteness", sourceProfile.coreWhiteness)
            setFloat("shellVisibility", sourceProfile.shellVisibility)
            setFloat("sourceHaloSpread", sourceProfile.haloSpread)
            setFloat("solidCoreFill", 0.0f)
            setFloat("outerShellOpacity", 1.0f)
            setFloat("distortionOpacity", 1.0f)
            setFloat("time", getTime(tickDelta))
            sphereBuffer.draw()
            matrices.popMatrix()
        }
        RenderSystem.disableCull()
        RenderSystem.depthMask(true)
    }

    private fun currentWorldPosition(): Vector3f {
        return worldPosition.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
    }

    private fun transitionRadius(): Float {
        return max(radius * 0.92f, 0.12f)
    }

    private fun computeBlend(context: ScreenGlowRenderContext) = DistanceAdaptiveGlow.computeOrbBlend(
        worldPosition = currentWorldPosition(),
        worldRadius = transitionRadius(),
        context = context,
        directFadeStartPx = DIRECT_FADE_START_PX,
        directFadeEndPx = DIRECT_FADE_END_PX,
        screenGlowFadeStartPx = 72.0f,
        screenGlowFadeEndPx = 18.0f
    )

    private fun computeOrbCompensation(projectedRadiusPx: Float): DistanceAdaptiveOrbGlowCompensation {
        val base = DistanceAdaptiveGlow.farOrbCompensationFromProjectedRadiusPx(projectedRadiusPx)
        val strength = distanceCompensation.coerceIn(0.0f, 1.0f)
        return DistanceAdaptiveOrbGlowCompensation(
            persistence = base.persistence * strength,
            radiusScale = mix(1.0f, base.radiusScale, strength),
            intensityScale = mix(1.0f, base.intensityScale, strength),
            softness = mix(0.58f, base.softness, strength)
        )
    }

    private fun createSourceProfile(projectedRadiusPx: Float): BrightSourceOrbProfile {
        return DistanceAdaptiveGlow.brightSourceOrbProfileFromProjectedRadiusPx(projectedRadiusPx)
    }

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

    private fun mix(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
