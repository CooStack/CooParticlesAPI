package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.effects.builtin.MaskBloomConfig
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityReleaseHook
import cn.coostack.cooparticlesapi.renderer.runtime.SharedModelMaskBloomInput
import cn.coostack.cooparticlesapi.renderer.runtime.SharedModelMaskBloomRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.shader.AdvancedShaderProgramBuilder
import cn.coostack.cooparticlesapi.renderer.shader.texture.IdentifierTexture
import cn.coostack.cooparticlesapi.renderer.shader.texture.SimpleTextures
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f

/**
 * RenderEntity V2 下的内容驱动 glow smoke example。
 *
 * 这个类展示的是第一类接口语义：
 * - world pass 直接绘制模型内容
 * - frame-post 复用同一套模型绘制数据写 glow mask
 * - glow 颜色来自模型纹理内容，而不是固定球体或淡蓝兜底
 */
@CooAutoRegister
class TestRendererEntity(world: Level? = null, pos: Vec3 = Vec3.ZERO) : AutoRenderEntity(world, pos),
    SharedModelMaskBloomRenderEntityRenderer<TestRendererEntity>,
    RenderEntityReleaseHook<TestRendererEntity> {
    constructor() : this(null, Vec3.ZERO)

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "test_renderer_model_glow"
        )

        private val magicTexture = SimpleTextures().apply {
            addTexture(
                IdentifierTexture(
                    ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test/magic.png")
                )
            )
        }
        private val texturedBillboardShader = AdvancedShaderProgramBuilder()
            .vertex("test/vtx/billboard.vsh")
            .fragment("test/frag/example/example.fsh")
            .build()
        private var visualResourcesReady = false
        private val billboardSize = Vector2f(4.8f, 4.8f)

        private fun ensureVisualResourcesInitialized() {
            if (visualResourcesReady) {
                return
            }
            RenderEntityExampleSupport.billboardBuffer()
            texturedBillboardShader.init()
            magicTexture.init()
            visualResourcesReady = true
        }

        private fun releaseVisualResources() {
            if (!visualResourcesReady) {
                return
            }
            texturedBillboardShader.release()
            magicTexture.release()
            visualResourcesReady = false
        }
    }

    override fun getRenderID(): ResourceLocation = ID

    override fun initialize(instance: RenderEntityInstance<TestRendererEntity>) {
        ensureVisualResourcesInitialized()
    }

    override fun glowMaskConfig(entity: TestRendererEntity): MaskBloomConfig {
        return BuiltinRenderEffectDescriptors.defaultRenderEntityModelGlowConfig().copy(
            blurSigma = 15.0f,
            blurRange = 10.0f,
            intensity = 2.8f,
            baseMaskIntensity = 0.24f,
            threshold = 0.0f,
            thresholdSoftness = 0.015f
        )
    }

    override fun renderSharedModel(input: SharedModelMaskBloomInput<TestRendererEntity>) {
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.depthMask(false)
        try {
            texturedBillboardShader.useOnContext {
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

    override fun release(instance: RenderEntityInstance<TestRendererEntity>) {
        releaseVisualResources()
    }
}
