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

internal fun persistentGlowSphereDirectOnlyBlend(baseBlend: DistanceAdaptiveGlowBlend): DistanceAdaptiveGlowBlend {
    val hasProjection = baseBlend.projectedRadiusPx > 1.0e-3f
    return DistanceAdaptiveGlowBlend(
        directWeight = if (hasProjection) 1.0f else 0.0f,
        screenGlowWeight = 0.0f,
        projectedRadiusPx = baseBlend.projectedRadiusPx
    )
}

/**
 * 演示“球体本体 emissive 持续写入同一条 post-process pipe”。
 *
 * 链路：
 * 1. 所有距离都继续使用真实球体 mesh 写入 glow / distortion 输入。
 * 2. 远景不再切换 billboard fallback，而是沿用球体本体 shader，并继续使用远距补偿参数。
 * 3. composite 仍然走 `persistentGlowSphereDistortion`，避免额外分支带来的观感跳变。
 */
class TestPersistentGlowSphereEntity(world: Level?) : RenderEntity(world) {
    companion object {
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

    private fun currentWorldPosition(): Vector3f {
        return worldPosition.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
    }

    private fun transitionRadius(): Float {
        return max(radius * 0.92f, 0.12f)
    }

    private fun computeBlend(context: ScreenGlowRenderContext) = persistentGlowSphereDirectOnlyBlend(
        DistanceAdaptiveGlow.computeOrbBlend(
            worldPosition = currentWorldPosition(),
            worldRadius = transitionRadius(),
            context = context
        )
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
