package cn.coostack.cooparticlesapi.test.options.renderer.cases

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.test.options.renderer.RenderEntityExampleSupport
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f
import org.joml.Vector3f

/**
 * 镜面反射案例。
 *
 * 这一版直接做成稳定的“浮空圆镜”：
 * - 世界里一定能看见一个镜面盘
 * - 镜面内部用视角相关的假反射表现
 * - 外圈有明确的高光边缘
 *
 * 它不再依赖 scene color/depth 的帧后处理，所以不会出现“镜子根本不渲染”的问题。
 */
@CooAutoRegister
class CaseMirrorShowcaseEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CaseMirrorShowcaseEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_mirror_showcase"
        )

        private val mirrorShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/mirror_disc.fsh")
            .build()

        private var shaderReady = false

        private fun initStatic() {
            if (shaderReady) {
                return
            }
            shaderReady = true
            RenderEntityExampleSupport.billboardBuffer()
            mirrorShader.init()
        }
    }

    @field:CodecField
    var radius: Float = 1.8f

    @field:CodecField
    var reflectivity: Float = 0.96f

    @field:CodecField
    var mirrorTint: Vector3f = Vector3f(0.86f, 0.92f, 1.00f)

    @field:CodecField
    var rimStrength: Float = 2.2f

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<CaseMirrorShowcaseEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: CaseMirrorShowcaseEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun renderLocal(input: LocalRenderInput<CaseMirrorShowcaseEntity>) {
        val entity = input.instance.entity
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.depthMask(false)
        try {
            mirrorShader.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat2("size", Vector2f(entity.radius * 2.0f, entity.radius * 2.0f))
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("mirrorTint", entity.mirrorTint)
                setFloat("reflectivity", entity.reflectivity)
                setFloat("rimStrength", entity.rimStrength)
                RenderEntityExampleSupport.billboardBuffer().draw()
            }
        } finally {
            RenderSystem.depthMask(true)
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
        }
    }
}
