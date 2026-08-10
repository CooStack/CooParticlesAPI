package cn.coostack.cooparticlesapi.test.options.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.blocks.CooBlocks
import cn.coostack.cooparticlesapi.renderer.pipeline.CooBlockPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffects
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block

/** 可直接运行的 block terrain shader 与独立屏幕效果示例。 */
object RenderPipelineExamples {
    @JvmField
    val ORIGINAL_TEXTURE_BLOCK = CooPipelines.block(id("example/original_texture_block")) {
        shader(id("terrain/original_texture"))
        inputBlockAtlas("BaseSampler")
        inputSceneColor("SceneColor", optional = true)
        effectUv(CooEffectUvMode.FACE_LOCAL)
        uniform("TintStrength", 0.35F)
    }

    /** 不采样原方块图集的示例模板；由测试调用方选择要绑定的原版方块。 */
    @JvmField
    val SOLID_TINT_BLOCK = CooPipelines.block(id("example/solid_tint_block")) {
        shader(id("terrain/solid_tint"))
        effectUv(CooEffectUvMode.WORLD_XZ)
        uniform("EffectTint", CooUniformValue.Vec3Value(0.1F, 0.85F, 0.35F))
    }

    @JvmField
    val HEAT_HAZE = CooShaderEffects.register(id("heat_haze")) {
        fragment(id("post/screen_distortion.fsh"))
        inputSceneColor("scene")
        outputToScreen()
    }

    fun registerBlockExamples() {
        CooBlockPipelines.bind(CooBlocks.TEST_CONTROLLER.get(), ORIGINAL_TEXTURE_BLOCK)
    }

    /** 显式运行忽略原纹理的原版方块示例，不在客户端启动时改写任何原版方块。 */
    fun bindVanillaBlockExample(block: Block) {
        CooBlockPipelines.bind(block, SOLID_TINT_BLOCK)
    }

    fun playHeatHaze() = HEAT_HAZE.play {
        duration(30)
        uniform("strength", 0.12F)
        uniform("radius", 0.35F)
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
