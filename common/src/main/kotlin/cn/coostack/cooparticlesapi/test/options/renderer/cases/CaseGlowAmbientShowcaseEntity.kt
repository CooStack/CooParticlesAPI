package cn.coostack.cooparticlesapi.test.options.renderer.cases

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.light.WorldLight
import cn.coostack.cooparticlesapi.renderer.light.WorldLightProvider
import cn.coostack.cooparticlesapi.renderer.light.WorldLightShape
import cn.coostack.cooparticlesapi.renderer.runtime.DedicatedGlowMaskRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.GlowMaskRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.test.options.renderer.RenderEntityExampleSupport
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * 内容驱动 glow + 环境光案例。
 *
 * 这个类展示第二类接口语义：
 * - world pass 仍然画本体模型
 * - glow mask 通过 `renderGlowMask(...)` 单独绘制
 * - glow 仍然来自局部模型内容，而不是 screen-space 球体公式
 */
@CooAutoRegister
class CaseGlowAmbientShowcaseEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) :
    AutoRenderEntity(world, pos),
    WorldLightProvider,
    WorldPassRenderEntityRenderer<CaseGlowAmbientShowcaseEntity>,
    DedicatedGlowMaskRenderEntityRenderer<CaseGlowAmbientShowcaseEntity> {

    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "case_glow_ambient_showcase"
        )

        private val localSphereShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/world_sphere.vsh")
            .fragment("test/frag/showcase_energy_sphere.fsh")
            .build()

        private var shaderReady = false

        private fun initStatic() {
            if (shaderReady) {
                return
            }
            shaderReady = true
            RenderEntityExampleSupport.sphereBuffer()
            localSphereShader.init()
        }
    }

    @field:CodecField
    var radius: Float = 2.1f

    @field:CodecField
    var glowColor: Vector3f = Vector3f(1.00f, 0.88f, 0.58f)

    @field:CodecField
    var glowIntensity: Float = 2.7f

    @field:CodecField
    var lightIntensity: Float = 1.8f

    override fun getRenderID(): ResourceLocation = ID

    override fun describeFeatures(entity: CaseGlowAmbientShowcaseEntity): RenderEntityFeatureSet {
        return BuiltinRenderEffectDescriptors.describeBuiltinProviders(entity).merge(
            RenderEntityFeatureSet(
                stages = setOf(RenderFrameStage.WORLD_PASS, RenderFrameStage.FRAME_POST),
                requestedSceneTargets = setOf(
                    RenderSceneTargets.POST,
                    RenderSceneTargets.SCENE_COLOR,
                    RenderSceneTargets.SCENE_DEPTH,
                    RenderSceneTargets.LIGHT
                ),
                effectTypes = setOf(
                    BuiltinRenderEffectTypes.MASK_BLOOM,
                    BuiltinRenderEffectTypes.WORLD_LIGHT
                ),
                localRendererEnabled = true,
                effectGraphEnabled = true
            )
        )
    }

    override fun initialize(instance: RenderEntityInstance<CaseGlowAmbientShowcaseEntity>) {
        initStatic()
    }

    override fun renderLocal(input: LocalRenderInput<CaseGlowAmbientShowcaseEntity>) {
        val entity = input.instance.entity
        drawSphereModel(
            tickDelta = input.tickDelta,
            viewMatrix = input.viewMatrix,
            projMatrix = input.projMatrix,
            modelMatrix = Matrix4f(input.modelMatrix),
            color = entity.glowColor,
            intensity = entity.glowIntensity * 0.62f,
            alpha = 0.12f,
            rimPower = 3.1f,
            fillStrength = 0.92f
        )
    }

    override fun glowMaskEffectId(entity: CaseGlowAmbientShowcaseEntity): String {
        return "${ID}#ambient_glow_mask"
    }

    override fun glowMaskConfig(entity: CaseGlowAmbientShowcaseEntity): MaskBloomConfig {
        return BuiltinRenderEffectDescriptors.defaultRenderEntityModelGlowConfig().copy(
            blurSigma = 14.0f,
            blurRange = 9.0f,
            intensity = entity.glowIntensity * 1.2f,
            baseMaskIntensity = 0.20f,
            threshold = 0.0f,
            thresholdSoftness = 0.02f
        )
    }

    override fun renderGlowMask(input: GlowMaskRenderInput<CaseGlowAmbientShowcaseEntity>) {
        val entity = input.instance.entity
        drawSphereModel(
            tickDelta = input.tickDelta,
            viewMatrix = input.viewMatrix,
            projMatrix = input.projMatrix,
            modelMatrix = input.modelMatrix,
            color = entity.glowColor,
            intensity = entity.glowIntensity,
            alpha = 0.40f,
            rimPower = 2.35f,
            fillStrength = 1.16f
        )
    }

    override fun collectWorldLights(tickDelta: Float, output: MutableList<WorldLight>) {
        output.add(
            WorldLight(
                position = Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()),
                color = Vector3f(glowColor),
                radius = radius * 3.8f,
                intensity = lightIntensity * 1.4f,
                normal = Vector3f(0.0f, 1.0f, 0.0f),
                shape = WorldLightShape.DISK,
                softness = 0.48f
            )
        )
    }

    private fun drawSphereModel(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4f,
        color: Vector3f,
        intensity: Float,
        alpha: Float,
        rimPower: Float,
        fillStrength: Float
    ) {
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.depthMask(false)
        try {
            localSphereShader.useOnContext {
                setMatrix4("projMat", projMatrix)
                setMatrix4("viewMat", viewMatrix)
                setMatrix4("transMat", Matrix4f(modelMatrix).scale(radius))
                setFloat("time", getTime(tickDelta))
                setFloat3("color", color)
                setFloat("intensity", intensity)
                setFloat("alpha", alpha)
                setFloat("rimPower", rimPower)
                setFloat("fillStrength", fillStrength)
                setFloat("pulseSpeed", 1.0f)
                setFloat("noiseScale", 2.8f)
                RenderEntityExampleSupport.sphereBuffer().draw()
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
