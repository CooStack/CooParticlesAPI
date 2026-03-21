package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectTypes
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.runtime.FramePostRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.LocalRenderInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionCollector
import cn.coostack.cooparticlesapi.renderer.runtime.RenderContributionInput
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityFeatureSet
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityReleaseHook
import cn.coostack.cooparticlesapi.renderer.runtime.WorldPassRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f


/**
 * AI别动这里的代码
 *
 */
@CooAutoRegister
class ExampleRendererEntity(world: Level?, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    FramePostRenderEntityRenderer<ExampleRendererEntity>,
    WorldPassRenderEntityRenderer<ExampleRendererEntity>,
    RenderEntityReleaseHook<ExampleRendererEntity> {
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "example_renderer_entity")
        private val magicTexture = SimpleTextures().apply {
            addTexture(
                IdentifierTexture(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test/magic.png")
                )
            )
        }
        private val board = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/example/example.fsh")
            .build()
        private var visualResourcesReady = false
        private val billboardSize = Vector2f(10f, 10f)
        private const val bloomBillboardScale = 10.8f
        private val bloomSourceTint = Vector4f(3.6f, 3.6f, 3.6f, 1.0f)

        private fun ensureVisualResourcesInitialized() {
            if (visualResourcesReady) {
                return
            }
            board.init()
            magicTexture.init()
            visualResourcesReady = true
        }

        private fun releaseVisualResources() {
            if (!visualResourcesReady) {
                return
            }
            board.release()
            magicTexture.release()
            visualResourcesReady = false
        }
    }

    override fun initialize(instance: RenderEntityInstance<ExampleRendererEntity>) {
        ensureVisualResourcesInitialized()
    }

    override fun getRenderID(): ResourceLocation {
        return ID
    }

    override fun describeFeatures(entity: ExampleRendererEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(
                RenderFrameStage.WORLD_PASS,
                RenderFrameStage.FRAME_POST
            ),
            requestedSceneTargets = setOf(
                RenderSceneTargets.POST,
                RenderSceneTargets.SCENE_COLOR,
                RenderSceneTargets.SCENE_DEPTH
            ),
            effectTypes = setOf(BuiltinRenderEffectTypes.MASK_BLOOM),
            localRendererEnabled = true,
            effectGraphEnabled = true,
        )
    }

    override fun renderLocal(input: LocalRenderInput<ExampleRendererEntity>) {
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.depthMask(false)
        try {
            board.useOnContext {
                setMatrix4("projMat", input.projMatrix)
                setMatrix4("viewMat", input.viewMatrix)
                setMatrix4("transMat", input.modelMatrix)
                setFloat2("size", billboardSize)
                setInt("tex", 0)
                magicTexture.drawWith {
                    RenderEntityExampleSupport.billboardBuffer().draw()
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

    override fun collectRenderContributions(
        input: RenderContributionInput<ExampleRendererEntity>,
        collector: RenderContributionCollector
    ) {
        collector.submit(
            BuiltinRenderEffectDescriptors.maskBloomTexturedBillboard(
                effectId = ID.toString(),
                sourceInstanceId = uuid.toString(),
                frameContext = input.frameContext,
                textures = magicTexture,
                modelMatrix = Matrix4f(
                    RenderEntityExampleSupport.buildModelMatrix(this, input.frameContext.tickDelta).scale(bloomBillboardScale)
                ),
                sourceEntity = this,
                config = MaskBloomConfig(
                    blurSigma = 18.0f,
                    blurRange = 8.0f,
                    intensity = 1.5f,
                    baseMaskIntensity = 0.04f,
                    threshold = 0.0f,
                    thresholdSoftness = 0.01f,
                    tint = Vector3f(1.0f, 1.0f, 1.0f)
                ),
                tint = Vector4f(bloomSourceTint),
                sourceBoost = 2.0f
            )
        )
    }
    override fun release(instance: RenderEntityInstance<ExampleRendererEntity>) {
        releaseVisualResources()
    }
}
