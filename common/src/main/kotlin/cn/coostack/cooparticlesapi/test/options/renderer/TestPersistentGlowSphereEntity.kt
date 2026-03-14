package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityInputBlendMode
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.glow.BrightSourceOrbProfile
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlowBlend
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveOrbGlowCompensation
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
import kotlin.math.max

/**
 * 演示“球体本体 emissive + 同一条 post-process pipe 的远距 billboard fallback”。
 *
 * 链路：
 * 1. 近景继续使用真实球体 mesh 写入 glow / distortion 输入。
 * 2. 远景不再交给 `PersistentBloom`，改为在同一条 pipe 内写入一个常像素尺寸 billboard。
 * 3. composite 仍然走 `persistentGlowSphereDistortion`，但远景 fallback 不再写 distortion，
 *    从而避免远处把整屏背景洗灰。
 */
class TestPersistentGlowSphereEntity(world: Level?) : RenderEntity(world) {
    companion object {
        private const val DIRECT_FADE_START_PX = 24.0f
        private const val DIRECT_FADE_END_PX = 7.0f

        val id: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_persistent_glow_sphere"
        )

        /**
         * `RenderEntity.createCodec(...)` 会自动处理 `uuid/pos/canceled/age`，
         * 这里仅同步球体外观相关的额外字段。
         */
        val codec: StreamCodec<FriendlyByteBuf, RenderEntity> = RenderEntity.createCodec(
            { TestPersistentGlowSphereEntity(null) },
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

        private val glowShader = ShaderProgramBuilder()
            .vertex("test/vtx/glow_sphere.vsh")
            .fragment("test/frag/glow_sphere.fsh")
            .build()

        private val billboardShader = ShaderProgramBuilder()
            .vertex("test/vtx/glow_sphere_screen_billboard.vsh")
            .fragment("test/frag/glow_sphere_screen_billboard.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            sphereBuffer.init()
            quadBuffer.init()
            glowShader.init()
            billboardShader.init()
        }

        fun reloadStaticResources() {
            sphereBuffer.release()
            quadBuffer.release()
            glowShader.release()
            billboardShader.release()
            initialized = false
        }
    }

    var radius by tracked(4.1f)
    var intensity by tracked(6.8f)
    var haloIntensity by tracked(3.4f)
    var haloRadiusScale by tracked(1.72f)
    var fresnelStrength by tracked(1.38f)
    var distanceCompensation by tracked(1.0f)
    var animationSpeed by tracked(1.08f)
    var overbrightClamp by tracked(6.6f)
    var glowColor by tracked(Vector3f(0.58f, 0.88f, 1.30f))

    private val worldPosition = Vector3f()
    private val billboardSizePx = Vector2f()

    override fun initialize() {
        initStatic()
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = codec

    override fun getRenderID(): ResourceLocation = id

    override fun getRenderPass(): RenderEntityRenderPass = RenderEntityRenderPass.POST_PROCESS

    override fun getInputBlendMode(): RenderEntityInputBlendMode {
        return RenderEntityInputBlendMode.ALPHA
    }

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
        val compensation = computeOrbCompensation(blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)
        drawDirectSphere(
            matrices = matrices,
            viewMatrix = viewMatrix,
            projMatrix = projMatrix,
            tickDelta = tickDelta,
            blend = blend,
            compensation = compensation,
            sourceProfile = sourceProfile
        )
//        drawFarBillboard(
//            matrices = matrices,
//            viewMatrix = viewMatrix,
//            projMatrix = projMatrix,
//            tickDelta = tickDelta,
//            context = context,
//            blend = blend,
//            compensation = compensation,
//            sourceProfile = sourceProfile
//        )
    }

    private fun drawDirectSphere(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float,
        blend: DistanceAdaptiveGlowBlend,
        compensation: DistanceAdaptiveOrbGlowCompensation,
        sourceProfile: BrightSourceOrbProfile
    ) {
        if (blend.directWeight <= 1.0e-3f) {
            return
        }
        val directProfile =
            DistanceAdaptiveGlow.persistentDirectSphereProfileFromProjectedRadiusPx(blend.projectedRadiusPx)

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
            setFloat("solidCoreFill", directProfile.solidCoreFill)
            setFloat("outerShellOpacity", directProfile.outerShellOpacity)
            setFloat("distortionOpacity", directProfile.distortionOpacity)
            setFloat("time", getTime(tickDelta))
            sphereBuffer.draw()
            matrices.popMatrix()
        }
        RenderSystem.disableCull()
        RenderSystem.depthMask(true)
    }

    private fun drawFarBillboard(
        matrices: Matrix4fStack,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        tickDelta: Float,
        context: ScreenGlowRenderContext,
        blend: DistanceAdaptiveGlowBlend,
        compensation: DistanceAdaptiveOrbGlowCompensation,
        sourceProfile: BrightSourceOrbProfile
    ) {
        val billboardWeight = computeBillboardWeight(blend)
        if (billboardWeight <= 1.0e-3f) {
            return
        }

        val billboardDiameterPx = computeBillboardSizePx(blend, compensation, sourceProfile)
        val billboardCoreIntensity = computeBillboardCoreIntensity(compensation)
        val billboardHaloIntensity = computeBillboardHaloIntensity(compensation)
        if (billboardDiameterPx <= 1.0e-3f || billboardHaloIntensity <= 1.0e-3f) {
            return
        }

        RenderSystem.disableCull()
        RenderSystem.depthMask(false)
        billboardShader.useOnContext {
            setMatrix4("projMat", projMatrix)
            setMatrix4("viewMat", viewMatrix)
            setMatrix4("transMat", matrices)
            setFloat2("screenSize", context.screenSize)
            billboardSizePx.set(billboardDiameterPx, billboardDiameterPx)
            setFloat2("sizePx", billboardSizePx)
            setFloat3("color", glowColor)
            setFloat("intensity", billboardCoreIntensity)
            setFloat("haloIntensity", billboardHaloIntensity)
            setFloat("opacity", billboardWeight)
            setFloat("softness", compensation.softness)
            setFloat("coreWhiteness", sourceProfile.coreWhiteness)
            setFloat("haloSpread", sourceProfile.haloSpread)
            setFloat("overbrightClamp", overbrightClamp)
            setFloat("time", getTime(tickDelta))
            quadBuffer.draw()
        }
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

    private fun computeBillboardWeight(blend: DistanceAdaptiveGlowBlend): Float {
        val farWeight = blend.screenGlowWeight.coerceIn(0.0f, 1.0f)
        val directSuppression = (1.0f - blend.directWeight).coerceIn(0.0f, 1.0f)
        return (farWeight * (0.32f + directSuppression * 0.68f)).coerceIn(0.0f, 1.0f)
    }

    private fun computeBillboardSizePx(
        blend: DistanceAdaptiveGlowBlend,
        compensation: DistanceAdaptiveOrbGlowCompensation,
        sourceProfile: BrightSourceOrbProfile
    ): Float {
        val projectedDiameter = blend.projectedRadiusPx * 2.0f
        val amplifiedDiameter = projectedDiameter *
            mix(1.20f, 2.10f + haloRadiusScale * 0.16f, compensation.persistence) *
            (0.84f + sourceProfile.haloSpread * 0.18f)
        val minimumDiameter = mix(12.0f, 18.0f + haloRadiusScale * 1.6f, compensation.persistence)
        return max(amplifiedDiameter, minimumDiameter).coerceIn(10.0f, 40.0f)
    }

    private fun computeBillboardCoreIntensity(compensation: DistanceAdaptiveOrbGlowCompensation): Float {
        val baseIntensity = intensity * 0.34f + haloIntensity * 0.18f
        return (baseIntensity * compensation.intensityScale).coerceAtMost(6.4f)
    }

    private fun computeBillboardHaloIntensity(compensation: DistanceAdaptiveOrbGlowCompensation): Float {
        val baseIntensity = haloIntensity * 0.42f + intensity * 0.12f
        return (
            baseIntensity *
                (0.92f + compensation.persistence * 0.36f) *
                compensation.intensityScale
            ).coerceAtMost(8.2f)
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
