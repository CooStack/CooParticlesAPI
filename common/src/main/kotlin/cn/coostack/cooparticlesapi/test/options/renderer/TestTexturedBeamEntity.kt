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
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import cn.coostack.cooparticlesapi.renderer.shader.vertex.SimpleVertexBuffer
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 老的激光测试实体。
 *
 * 过去这里使用 `IdentifierTexture` 上传本地 png，再在客户端初始化时做 beam 纹理绘制。
 * 该链路在部分驱动上会在 `glTexImage2D` 直接 native crash，所以这里改成纯程序化 beam。
 *
 * 保留原类名和测试入口，是为了不影响现有 `APITestGroupBuilder` 顺序与老测试编号。
 */
@CooAutoRegister
class TestTexturedBeamEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    ScreenGlowContextProvider,
    WorldPassRenderEntityRenderer<TestTexturedBeamEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        private val GLOW_SAMPLE_OFFSETS = floatArrayOf(-0.36f, 0.0f, 0.36f)

        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_textured_beam"
        )

        private val beamBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genCylinder(1.0f, 18f, 1.0f),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val beamShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_beam.vsh")
            .fragment("test/frag/procedural_beam_volume.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            beamBuffer.init()
            beamShader.init()
        }
    }

    @field:CodecField
    var width: Float = 0.18f

    @field:CodecField
    var height: Float = 6.0f

    @field:CodecField
    var beamColor: Vector4f = Vector4f(0.38f, 0.92f, 1.18f, 0.92f)

    @field:CodecField
    var beamDirection: Vector3f = Vector3f(0.0f, 1.0f, 0.0f)

    @field:CodecField
    var pulseSpeed: Float = 1.35f

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

        val direction = Vector3f(beamDirection)
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
            beamShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.rotate(rotation)
                input.modelMatrix.scale(width, height, width)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat3(
                    "beamColor",
                    Vector3f(beamColor.x, beamColor.y, beamColor.z).mul(blend.directWeight)
                )
                setFloat("time", getTime(input.tickDelta))
                setFloat("pulseSpeed", pulseSpeed)
                setFloat("alpha", beamColor.w * blend.directWeight)
                beamBuffer.draw()
                input.modelMatrix.popMatrix()
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

        val beamAxis = Vector3f(beamDirection)
        if (beamAxis.lengthSquared() < 1.0e-4f) {
            beamAxis.set(0.0f, 1.0f, 0.0f)
        }
        beamAxis.normalize()
        val glowColor = Vector3f(beamColor.x, beamColor.y, beamColor.z)
        val sampleIntensity = beamColor.w * (1.6f * blend.screenGlowWeight) / GLOW_SAMPLE_OFFSETS.size
        val sampleRadius = max(width * 1.4f, height * 0.10f)

        for (offset in GLOW_SAMPLE_OFFSETS) {
            val samplePos = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
                .add(Vector3f(beamAxis).mul(height * (offset + 0.5f)))
            output.add(
                ScreenGlow(
                    position = samplePos,
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
