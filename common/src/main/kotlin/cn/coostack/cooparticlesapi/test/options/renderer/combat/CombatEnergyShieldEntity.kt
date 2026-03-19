package cn.coostack.cooparticlesapi.test.options.renderer.combat

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
import kotlin.math.sin

/**
 * 实战案例：能量护盾。
 *
 * 这一版只做稳定的世界空间护盾壳：
 * - 大范围、蓝色、半透明
 * - 外缘更亮
 * - 表面有移动的网格/能量条纹
 *
 * 它和充能核心的区别会非常直接：护盾是“外壳”，充能是“热核”。
 */
@CooAutoRegister
class CombatEnergyShieldEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CombatEnergyShieldEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "combat_energy_shield"
        )

        private val shieldShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/shield_shell_local.fsh")
            .build()

        private var shadersReady = false

        private fun initStatic() {
            if (shadersReady) {
                return
            }
            shadersReady = true
            RenderEntityExampleSupport.sphereBuffer()
            shieldShader.init()
        }
    }

    @field:CodecField
    var radius: Float = 2.5f

    @field:CodecField
    var shieldStrength: Float = 0.72f

    @field:CodecField
    var shieldColor: Vector3f = Vector3f(0.36f, 0.88f, 1.24f)

    override fun getRenderID(): ResourceLocation = ID

    override fun serverTick() {
        shieldStrength = 0.68f + 0.20f * ((sin(age * 0.16f) + 1.0f) * 0.5f).toFloat()
        if (age % 6 == 0) {
            requestSync()
        }
    }

    override fun initialize(instance: RenderEntityInstance<CombatEnergyShieldEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: CombatEnergyShieldEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun renderLocal(input: LocalRenderInput<CombatEnergyShieldEntity>) {
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
            shieldShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.radius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("shieldColor", entity.shieldColor)
                setFloat("shieldStrength", entity.shieldStrength)
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
