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
import kotlin.math.cos
import kotlin.math.sin

/**
 * 实战案例：充能核心。
 *
 * 这个版本刻意做成“热核聚能”风格：
 * - 中心是高亮的白金色核心
 * - 外层是偏暖色的能量壳
 * - 周围再画几颗绕核旋转的小火花
 *
 * 这样它会和蓝色的大型能量护盾形成非常明显的视觉区分。
 */
@CooAutoRegister
class CombatChargeEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldPassRenderEntityRenderer<CombatChargeEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "combat_charge"
        )

        private val coreShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/showcase_energy_sphere.fsh")
            .build()

        private var shadersReady = false

        private fun initStatic() {
            if (shadersReady) {
                return
            }
            shadersReady = true
            RenderEntityExampleSupport.sphereBuffer()
            coreShader.init()
        }
    }

    @field:CodecField
    var radius: Float = 0.95f

    @field:CodecField
    var charge: Float = 0.0f

    @field:CodecField
    var coreColor: Vector3f = Vector3f(1.16f, 0.92f, 0.44f)

    override fun getRenderID(): ResourceLocation = ID

    override fun serverTick() {
        charge = ((sin(age * 0.10f) + 1.0f) * 0.5f).toFloat()
        radius = 0.82f + charge * 0.22f
        if (age % 6 == 0) {
            requestSync()
        }
    }

    override fun initialize(instance: RenderEntityInstance<CombatChargeEntity>) {
        initStatic()
    }

    override fun describeFeatures(entity: CombatChargeEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun renderLocal(input: LocalRenderInput<CombatChargeEntity>) {
        val entity = input.instance.entity
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE
        )
        RenderSystem.depthMask(false)
        try {
            coreShader.useOnContext {
                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.radius)
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat("time", entity.getTime(input.tickDelta))
                setFloat3("color", entity.coreColor)
                setFloat("intensity", 2.2f + entity.charge * 2.2f)
                setFloat("alpha", 0.24f)
                setFloat("rimPower", 2.1f)
                setFloat("fillStrength", 0.70f)
                setFloat("pulseSpeed", 1.8f + entity.charge * 1.2f)
                setFloat("noiseScale", 3.8f)
                RenderEntityExampleSupport.sphereBuffer().draw()
                input.modelMatrix.popMatrix()

                input.modelMatrix.pushMatrix()
                input.modelMatrix.scale(entity.radius * 0.52f)
                setMatrix4("transMat", input.modelMatrix)
                setFloat3("color", Vector3f(1.30f, 1.16f, 0.92f))
                setFloat("intensity", 3.4f + entity.charge * 3.0f)
                setFloat("alpha", 0.38f)
                setFloat("rimPower", 1.2f)
                setFloat("fillStrength", 0.40f)
                setFloat("pulseSpeed", 2.8f)
                setFloat("noiseScale", 2.4f)
                RenderEntityExampleSupport.sphereBuffer().draw()
                input.modelMatrix.popMatrix()

                val orbitRadius = entity.radius * (1.25f + entity.charge * 0.45f)
                repeat(3) { index ->
                    val angle = entity.getTime(input.tickDelta) * (1.8f + index * 0.35f) + index * 2.094f
                    val x = cos(angle) * orbitRadius
                    val y = sin(angle * 1.2f) * orbitRadius * 0.35f
                    val z = sin(angle) * orbitRadius
                    input.modelMatrix.pushMatrix()
                    input.modelMatrix.translate(x, y, z)
                    input.modelMatrix.scale(entity.radius * 0.16f)
                    setMatrix4("transMat", input.modelMatrix)
                    setFloat3("color", Vector3f(1.18f, 0.82f, 0.26f))
                    setFloat("intensity", 1.6f + entity.charge * 1.4f)
                    setFloat("alpha", 0.26f)
                    setFloat("rimPower", 1.4f)
                    setFloat("fillStrength", 0.52f)
                    setFloat("pulseSpeed", 3.2f)
                    setFloat("noiseScale", 2.2f)
                    RenderEntityExampleSupport.sphereBuffer().draw()
                    input.modelMatrix.popMatrix()
                }
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
