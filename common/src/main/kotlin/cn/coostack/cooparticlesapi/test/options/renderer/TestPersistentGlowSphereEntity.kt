package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.glow.BrightSourceOrbProfile
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlow
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveGlowBlend
import cn.coostack.cooparticlesapi.renderer.glow.DistanceAdaptiveOrbGlowCompensation
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.ShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
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
import kotlin.math.max

internal fun persistentGlowSphereDirectOnlyBlend(baseBlend: DistanceAdaptiveGlowBlend): DistanceAdaptiveGlowBlend {
    val hasProjection = baseBlend.projectedRadiusPx > 1.0e-3f
    return DistanceAdaptiveGlowBlend(
        directWeight = if (hasProjection) 1.0f else 0.0f,
        screenGlowWeight = 0.0f,
        projectedRadiusPx = baseBlend.projectedRadiusPx
    )
}

@CooAutoRegister
class TestPersistentGlowSphereEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos) {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_persistent_glow_sphere"
        )
    }

    @field:CodecField
    var radius: Float = 4.1f

    @field:CodecField
    var intensity: Float = 6.8f

    @field:CodecField
    var haloIntensity: Float = 3.4f

    @field:CodecField
    var haloRadiusScale: Float = 1.72f

    @field:CodecField
    var fresnelStrength: Float = 1.38f

    @field:CodecField
    var distanceCompensation: Float = 1.0f

    @field:CodecField
    var animationSpeed: Float = 1.08f

    @field:CodecField
    var overbrightClamp: Float = 6.6f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(0.58f, 0.88f, 1.30f)

    override fun getRenderID(): ResourceLocation = ID
}

class TestPersistentGlowSphereEntityRenderer : RenderEntityRenderer<TestPersistentGlowSphereEntity> {
    companion object {
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
    }

    private val worldPosition = Vector3f()

    override fun initialize(instance: RenderEntityInstance<TestPersistentGlowSphereEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<TestPersistentGlowSphereEntity>) {
        val entity = input.instance.entity
        val context = createGlowContext(input)
        val blend = computeBlend(entity, context)
        val compensation = computeOrbCompensation(entity, blend.projectedRadiusPx)
        val sourceProfile = createSourceProfile(blend.projectedRadiusPx)
        drawDirectSphere(
            entity = entity,
            input = input,
            blend = blend,
            compensation = compensation,
            sourceProfile = sourceProfile
        )
    }

    private fun drawDirectSphere(
        entity: TestPersistentGlowSphereEntity,
        input: LocalRenderInput<TestPersistentGlowSphereEntity>,
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
        try {
            glowShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.radius, entity.radius, entity.radius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat3("color", entity.glowColor)
                setFloat("intensity", entity.intensity)
                setFloat("haloIntensity", entity.haloIntensity)
                setFloat("haloRadiusScale", entity.haloRadiusScale)
                setFloat("fresnelStrength", entity.fresnelStrength)
                setFloat("animationSpeed", entity.animationSpeed)
                setFloat("projectedRadiusPx", blend.projectedRadiusPx)
                setFloat("farPersistence", compensation.persistence)
                setFloat("directOpacity", blend.directWeight)
                setFloat("overbrightClamp", entity.overbrightClamp)
                setFloat("coreWhiteness", sourceProfile.coreWhiteness)
                setFloat("shellVisibility", sourceProfile.shellVisibility)
                setFloat("sourceHaloSpread", sourceProfile.haloSpread)
                setFloat("solidCoreFill", directProfile.solidCoreFill)
                setFloat("outerShellOpacity", directProfile.outerShellOpacity)
                setFloat("distortionOpacity", directProfile.distortionOpacity)
                setFloat("time", entity.getTime(input.tickDelta))
                sphereBuffer.draw()
                input.modelMatrix.popMatrix()
            }
        } finally {
            RenderSystem.disableCull()
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
        }
    }

    private fun currentWorldPosition(entity: TestPersistentGlowSphereEntity): Vector3f {
        return worldPosition.set(entity.pos.x.toFloat(), entity.pos.y.toFloat(), entity.pos.z.toFloat())
    }

    private fun transitionRadius(entity: TestPersistentGlowSphereEntity): Float {
        return max(entity.radius * 0.92f, 0.12f)
    }

    private fun computeBlend(entity: TestPersistentGlowSphereEntity, context: ScreenGlowRenderContext) =
        persistentGlowSphereDirectOnlyBlend(
            DistanceAdaptiveGlow.computeOrbBlend(
                worldPosition = currentWorldPosition(entity),
                worldRadius = transitionRadius(entity),
                context = context
            )
        )

    private fun computeOrbCompensation(
        entity: TestPersistentGlowSphereEntity,
        projectedRadiusPx: Float
    ): DistanceAdaptiveOrbGlowCompensation {
        val base = DistanceAdaptiveGlow.farOrbCompensationFromProjectedRadiusPx(projectedRadiusPx)
        val strength = entity.distanceCompensation.coerceIn(0.0f, 1.0f)
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

    private fun createGlowContext(input: LocalRenderInput<TestPersistentGlowSphereEntity>): ScreenGlowRenderContext {
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

    private fun mix(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
