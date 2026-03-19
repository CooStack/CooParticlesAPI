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
import org.joml.Vector3f

/**
 * 水球案例。
 *
 * 这一版不再依赖屏幕后处理去硬做真实场景折射，
 * 而是改成稳定的世界空间“水膜 / 假折射 / 焦散高光”表现：
 * - 先保证你一眼能看出这是水球
 * - 再通过视角相关的折射色偏，给它“像在折射环境”的感觉
 *
 * 这样做的原因很直接：当前仓库的默认 framebuffer / post target 路径并不稳定，
 * 用 world pass 才能保证这个案例稳定展示。
 */
@CooAutoRegister
class CaseWaterOrbRefractionEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CaseWaterOrbRefractionEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_water_orb_refraction"
        )

        private val waterOrbShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/water_orb_local.fsh")
            .build()

        private var shadersReady = false

        private fun initStatic() {
            if (shadersReady) {
                return
            }
            shadersReady = true
            RenderEntityExampleSupport.sphereBuffer()
            waterOrbShader.init()
        }
    }

    @field:CodecField
    var radius: Float = 1.7f

    @field:CodecField
    var waterTint: Vector3f = Vector3f(0.28f, 0.70f, 1.04f)

    @field:CodecField
    var waveStrength: Float = 0.75f

    @field:CodecField
    var rimStrength: Float = 1.85f

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<CaseWaterOrbRefractionEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: CaseWaterOrbRefractionEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun renderLocal(input: LocalRenderInput<CaseWaterOrbRefractionEntity>) {
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
            waterOrbShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.radius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("waterTint", entity.waterTint)
                setFloat("waveStrength", entity.waveStrength)
                setFloat("rimStrength", entity.rimStrength)
                RenderEntityExampleSupport.sphereBuffer().draw()
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
}
