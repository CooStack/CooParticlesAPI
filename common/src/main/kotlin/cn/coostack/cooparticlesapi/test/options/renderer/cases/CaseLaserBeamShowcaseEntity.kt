package cn.coostack.cooparticlesapi.test.options.renderer.cases

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlow
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowContextProvider
import cn.coostack.cooparticlesapi.renderer.glow.ScreenGlowRenderContext
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

/**
 * 激光案例。
 *
 * 这个类重点讲两件事：
 * - `renderLocal()` 如何只靠程序化 shader 直接画一根激光，不依赖纹理。
 * - `ScreenGlowContextProvider` 如何把同一根激光再扩展成屏幕空间亮边。
 */
@CooAutoRegister
class CaseLaserBeamShowcaseEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    ScreenGlowContextProvider,
    WorldPassRenderEntityRenderer<CaseLaserBeamShowcaseEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        private val GLOW_SAMPLE_OFFSETS = floatArrayOf(-0.45f, 0.0f, 0.45f)

        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_laser_beam_showcase"
        )

        private val beamBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genCylinder(1.0f, 18f, 1.0f),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val laserShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_beam.vsh")
            .fragment("test/frag/procedural_beam_volume.fsh")
            .build()

        private var shaderReady = false

        private fun initStatic() {
            if (shaderReady) {
                return
            }
            shaderReady = true
            beamBuffer.init()
            laserShader.init()
        }
    }

    @field:CodecField
    var beamWidth: Float = 0.48f

    @field:CodecField
    var beamLength: Float = 5.8f

    @field:CodecField
    var beamColor: Vector4f = Vector4f(0.25f, 0.88f, 1.20f, 0.95f)

    @field:CodecField
    var beamDirection: Vector3f = Vector3f(0.0f, 1.0f, 0.0f)

    @field:CodecField
    var pulseSpeed: Float = 1.35f

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<CaseLaserBeamShowcaseEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<CaseLaserBeamShowcaseEntity>) {
        val entity = input.instance.entity
        val direction = Vector3f(entity.beamDirection)
        if (direction.lengthSquared() < 1.0e-4f) {
            direction.set(0.0f, 1.0f, 0.0f)
        }
        direction.normalize()
        val rotation = Quaternionf().rotateTo(Vector3f(0.0f, 1.0f, 0.0f), direction)

        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE
        )
        RenderSystem.depthMask(false)
        try {
            laserShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.rotate(rotation)
                input.modelMatrix.scale(entity.beamWidth, entity.beamLength, entity.beamWidth)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat3("beamColor", Vector3f(entity.beamColor.x, entity.beamColor.y, entity.beamColor.z))
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat("pulseSpeed", entity.pulseSpeed)
                setFloat("alpha", entity.beamColor.w)
                beamBuffer.draw()
                input.modelMatrix.popMatrix()
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
        }
    }

    override fun collectScreenGlows(context: ScreenGlowRenderContext, output: MutableList<ScreenGlow>) {
        val beamAxis = Vector3f(beamDirection)
        if (beamAxis.lengthSquared() < 1.0e-4f) {
            beamAxis.set(0.0f, 1.0f, 0.0f)
        }
        beamAxis.normalize()
        val sampleRadius = max(beamWidth * 0.95f, beamLength * 0.16f)
        val sampleIntensity = beamColor.w * 1.55f / GLOW_SAMPLE_OFFSETS.size

        for (offset in GLOW_SAMPLE_OFFSETS) {
            val samplePos = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
                .add(Vector3f(beamAxis).mul(beamLength * (offset + 0.5f)))
            output.add(
                ScreenGlow(
                    position = samplePos,
                    color = Vector3f(beamColor.x, beamColor.y, beamColor.z),
                    radius = sampleRadius,
                    intensity = sampleIntensity,
                    softness = 0.50f
                )
            )
        }
    }
}
