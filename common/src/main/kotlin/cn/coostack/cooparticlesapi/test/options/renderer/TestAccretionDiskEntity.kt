package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
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

data class TestAccretionDiskRenderRequest(
    val entity: TestAccretionDiskEntity,
    val frameContext: RenderFrameContext
)

@CooAutoRegister
class TestAccretionDiskEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    FramePostRenderEntityRenderer<TestAccretionDiskEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_accretion_disk"
        )
        val FRAME_POST_EFFECT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "effect/test_accretion_disk"
        )
        private val FRAME_POST_CAPABILITIES = setOf(
            RenderBackendCapability.FINAL_FRAME_POST,
            RenderBackendCapability.SCENE_COLOR_COPY,
            RenderBackendCapability.SCENE_DEPTH_READ
        )

        private val screenBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUVScreen(
                    Vector3f(-1f, 1f, 0f),
                    Vector3f(1f, 1f, 0f),
                    Vector3f(1f, -1f, 0f),
                    Vector3f(-1f, -1f, 0f)
                ),
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }

        private val sceneShader = AdvancedShaderProgramBuilder()
            .vertex("pipe/vertexes/screen.vsh")
            .fragment("test/frag/accretion_disk.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) return
            initialized = true
            screenBuffer.init()
            sceneShader.init()
        }

        fun renderRequests(requests: List<TestAccretionDiskRenderRequest>) {
            requests.forEach { request ->
                request.entity.renderFrameEffect(request.frameContext)
            }
        }
    }

    /**
     * 世界单位，既是 RenderEntity 影响域包围球半径，也是 shader 的积分裁剪边界。
     */
    @field:CodecField
    var radius: Float = 12.0f

    /**
     * Schwarzschild 事件视界半径 Rs，单位与世界坐标一致。
     * shader 内约定 G = c = 1。
     */
    @field:CodecField
    var schwarzschildRadius: Float = 0.92f

    @field:CodecField
    var diskInnerRadius: Float = 2.9f

    @field:CodecField
    var diskOuterRadius: Float = 7.8f

    @field:CodecField
    var diskHalfThickness: Float = 0.30f

    /**
     * 以 Kelvin 近似标定盘内缘温度。
     */
    @field:CodecField
    var diskTemperatureScale: Float = 9600.0f

    @field:CodecField
    var lensingStrength: Float = 1.08f

    @field:CodecField
    var spinSpeed: Float = 0.92f

    @field:CodecField
    var stepCount: Int = 168

    @field:CodecField
    var maxDistance: Float = 34.0f

    @field:CodecField
    var diskDensity: Float = 4.6f

    @field:CodecField
    var diskEmissionStrength: Float = 2.35f

    @field:CodecField
    var diskNormal: Vector3f = Vector3f(0f, 1f, 0f)

    /**
     * 盘的轻度色调偏置，不取代物理温度映射。
     */
    @field:CodecField
    var diskColor: Vector3f = Vector3f(1.18f, 1.06f, 0.78f)

    override fun getRenderID(): ResourceLocation = ID

    override fun describeFeatures(entity: TestAccretionDiskEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.FRAME_POST),
            requestedSceneTargets = setOf(
                RenderSceneTargets.POST,
                RenderSceneTargets.SCENE_COLOR,
                RenderSceneTargets.SCENE_DEPTH
            ),
            effectTypes = setOf(FRAME_POST_EFFECT),
            localRendererEnabled = false,
            effectGraphEnabled = true
        )
    }

    override fun initialize(instance: RenderEntityInstance<TestAccretionDiskEntity>) {
        initStatic()
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<TestAccretionDiskEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        collector.submit(
            RenderEffectDescriptor(
                effectType = FRAME_POST_EFFECT,
                effectId = ID.toString(),
                sourceInstanceId = entity.uuid.toString(),
                requiredCapabilities = FRAME_POST_CAPABILITIES,
                payload = TestAccretionDiskRenderRequest(entity, input.frameContext)
            )
        )
    }

    private fun renderFrameEffect(context: RenderFrameContext) {
        val minecraft = Minecraft.getInstance()
        val depthTextureId = minecraft.mainRenderTarget.depthTextureId
        if (depthTextureId <= 0) {
            return
        }
        val cameraPosition = minecraft.gameRenderer.mainCamera.position
        val cameraWorldPos = Vector3f(
            cameraPosition.x.toFloat(),
            cameraPosition.y.toFloat(),
            cameraPosition.z.toFloat()
        )
        val blackHoleWorldPos = Vector3f(
            pos.x.toFloat(),
            pos.y.toFloat(),
            pos.z.toFloat()
        )
        val blackHoleCameraRelativePos = Vector3f(blackHoleWorldPos).sub(cameraWorldPos)
        val normalizedDiskNormal = Vector3f(diskNormal).normalize()
        val inverseProjMatrix = Matrix4f(context.projMatrix).invert()
        val viewRotationMatrix = Matrix3f(context.viewMatrix)
        val inverseViewRotationMatrix = Matrix3f(viewRotationMatrix).invert()
        val effectRadiusWorld = maxOf(
            radius,
            diskOuterRadius + schwarzschildRadius * 4.5f,
            schwarzschildRadius * 6.0f
        )
        val lightingInfluenceWorld = maxOf(
            radius * 1.55f,
            diskOuterRadius * 2.15f + diskEmissionStrength * 3.40f,
            effectRadiusWorld * 1.30f
        )
        val traceDistanceWorld = maxOf(maxDistance, effectRadiusWorld * 2.35f)
        val screenSize = Vector2f(
            minecraft.mainRenderTarget.width.toFloat(),
            minecraft.mainRenderTarget.height.toFloat()
        )

        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        try {
            TestRelativisticShaderPipelines.renderAccretionDisk {
                sceneShader.useOnContext {
                    setMatrix4("projMat", context.projMatrix)
                    setMatrix4("inverseProjMat", inverseProjMatrix)
                    setMatrix3f("viewRotationMat", viewRotationMatrix)
                    setMatrix3f("inverseViewRotationMat", inverseViewRotationMatrix)
                    setFloat("time", getTime(context.tickDelta))
                    setFloat2("screenSize", screenSize)
                    setFloat3("blackHoleWorldPos", blackHoleWorldPos)
                    setFloat3("blackHoleCameraRelativePos", blackHoleCameraRelativePos)
                    setFloat("effectRadiusWorld", effectRadiusWorld)
                    setFloat("lightingInfluenceWorld", lightingInfluenceWorld)
                    setFloat("schwarzschildRadius", schwarzschildRadius)
                    setFloat("diskInnerRadius", diskInnerRadius)
                    setFloat("diskOuterRadius", diskOuterRadius)
                    setFloat("diskHalfThickness", diskHalfThickness)
                    setFloat("diskTemperatureScale", diskTemperatureScale)
                    setFloat("lensingStrength", lensingStrength)
                    setFloat("spinSpeed", spinSpeed)
                    setInt("stepCount", stepCount.coerceIn(48, 192))
                    setFloat("maxDistance", traceDistanceWorld)
                    setFloat("diskDensity", diskDensity)
                    setFloat("diskEmissionStrength", diskEmissionStrength)
                    setFloat3("diskColor", diskColor)
                    setFloat3("diskNormal", normalizedDiskNormal)
                    RenderSystem.setShaderTexture(7, depthTextureId)
                    setInt("sceneDepth", 7)
                    screenBuffer.draw()
                    RenderSystem.setShaderTexture(7, 0)
                }
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
        }
    }
}
