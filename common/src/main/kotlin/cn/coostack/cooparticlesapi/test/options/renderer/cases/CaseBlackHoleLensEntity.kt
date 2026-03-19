package cn.coostack.cooparticlesapi.test.options.renderer.cases

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.client.RenderUtil
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
import cn.coostack.cooparticlesapi.test.options.renderer.TestRelativisticShaderPipelines
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fStack
import org.joml.Vector3f

data class CaseBlackHoleLensRenderRequest(
    val entity: CaseBlackHoleLensEntity,
    val frameContext: RenderFrameContext
)

/**
 * 黑洞引力透镜案例。
 *
 * 这个类保留了原有黑洞实现的技术路线，但把注释改成了“读示例”视角：
 * - 真正的黑洞效果不在 world pass；
 * - `collectRenderContributions()` 提交的是一个自定义 frame-post descriptor；
 * - 黑洞 mask shader 只负责生成遮罩与扭曲信息；
 * - 最终 composite 交给 `TestRelativisticShaderPipelines.renderBlackHole(...)`。
 */
@CooAutoRegister
class CaseBlackHoleLensEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    FramePostRenderEntityRenderer<CaseBlackHoleLensEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_black_hole_lens"
        )
        val FRAME_POST_EFFECT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "effect/case_black_hole_lens"
        )
        private val FRAME_POST_CAPABILITIES = setOf(
            RenderBackendCapability.FINAL_FRAME_POST,
            RenderBackendCapability.SCENE_COLOR_COPY,
            RenderBackendCapability.SCENE_DEPTH_READ
        )

        private val sphereBuffer = SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genBall(1f, 96, 144),
                CooVertexFormat.POINT_FORMAT
            )
        }

        private val blackHoleShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/black_hole_mask.fsh")
            .build()

        private var initialized = false

        private fun initStatic() {
            if (initialized) {
                return
            }
            initialized = true
            sphereBuffer.init()
            blackHoleShader.init()
        }

        fun renderRequests(requests: List<CaseBlackHoleLensRenderRequest>) {
            requests.forEach { request ->
                request.entity.renderFrameEffect(request.frameContext)
            }
        }
    }

    @field:CodecField
    var radius: Float = 3.3f

    @field:CodecField
    var coreRadius: Float = 0.26f

    @field:CodecField
    var distortionStrength: Float = 1.18f

    @field:CodecField
    var diskNormal: Vector3f = Vector3f(0f, 1f, 0f)

    @field:CodecField
    var diskThickness: Float = 0.08f

    @field:CodecField
    var diskWidth: Float = 0.72f

    @field:CodecField
    var spinSpeed: Float = 0.88f

    @field:CodecField
    var ringColor: Vector3f = Vector3f(1.0f, 0.62f, 0.20f)

    override fun getRenderID(): ResourceLocation = ID

    override fun describeFeatures(entity: CaseBlackHoleLensEntity): RenderEntityFeatureSet {
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

    override fun initialize(instance: RenderEntityInstance<CaseBlackHoleLensEntity>) {
        initStatic()
    }

    override fun collectRenderContributions(
        input: RenderContributionInput<CaseBlackHoleLensEntity>,
        collector: RenderContributionCollector
    ) {
        val entity = input.instance.entity
        collector.submit(
            RenderEffectDescriptor(
                effectType = FRAME_POST_EFFECT,
                effectId = ID.toString(),
                sourceInstanceId = entity.uuid.toString(),
                requiredCapabilities = FRAME_POST_CAPABILITIES,
                payload = CaseBlackHoleLensRenderRequest(entity, input.frameContext)
            )
        )
    }

    private fun renderFrameEffect(frameContext: RenderFrameContext) {
        val matrices = Matrix4fStack(16)
        RenderUtil.setRenderStackWithEntity(matrices, this, frameContext.tickDelta)

        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        try {
            TestRelativisticShaderPipelines.renderBlackHole {
                blackHoleShader.useOnContext {
                    matrices.pushMatrix()
                    matrices.scale(radius, radius, radius)
                    setMatrix4("projMat", frameContext.projMatrix)
                    setMatrix4("viewMat", frameContext.viewMatrix)
                    setMatrix4("transMat", matrices)
                    setFloat("time", getTime(frameContext.tickDelta))
                    setFloat("coreRadius", coreRadius)
                    setFloat("distortionStrength", distortionStrength)
                    setFloat3("diskNormal", diskNormal)
                    setFloat("diskThickness", diskThickness)
                    setFloat("diskWidth", diskWidth)
                    setFloat("spinSpeed", spinSpeed)
                    setFloat3("ringColor", ringColor)
                    sphereBuffer.draw()
                    matrices.popMatrix()
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
