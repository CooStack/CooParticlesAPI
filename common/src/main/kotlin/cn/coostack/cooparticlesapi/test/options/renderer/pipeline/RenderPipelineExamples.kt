package cn.coostack.cooparticlesapi.test.options.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooBlockPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffectPlayback
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffects
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block

/**
 * 集中注册可直接运行的方块 Pipeline、命名 FBO 和独立屏幕效果示例。
 *
 * 客户端 tick 调用 [ensureStarfieldFbo] 维持星空 FBO。方块测试会把 [STARFIELD_BLOCK]
 * 临时绑定到模拟玩家位置处读取到的方块类型，不会修改测试控制器的默认渲染。
 */
object RenderPipelineExamples {
    /** 星空 FBO 效果的注册路径。 */
    private const val STARFIELD_FBO_ID = "starfield_fbo"

    /** 星空方块 Pipeline 的注册路径。 */
    private const val STARFIELD_BLOCK_ID = "example/starfield_fbo_block"

    /** 当前星空 FBO 的本地播放句柄；资源清理后 [isPlaying][CooShaderEffectPlayback.isPlaying] 会返回 `false`。 */
    private var starfieldPlayback: CooShaderEffectPlayback? = null

    /** 保留方块原纹理，并叠加场景颜色和绿色调色的方块示例。 */
    @JvmField
    val ORIGINAL_TEXTURE_BLOCK = CooPipelines.block(id("example/original_texture_block")) {
        shader(id("terrain/original_texture"))
        inputBlockAtlas("BaseSampler")
        inputSceneColor("SceneColor", optional = true)
        effectUv(CooEffectUvMode.FACE_LOCAL)
        uniform("EffectTint", CooUniformValue.Vec3Value(0.1F, 0.85F, 0.35F))
        uniform("EffectStrength", 0F)
        uniform("TintStrength", 0.35F)
    }

    /** 不采样原方块图集的示例模板；由测试调用方选择要绑定的原版方块。 */
    @JvmField
    val SOLID_TINT_BLOCK = CooPipelines.block(id("example/solid_tint_block")) {
        shader(id("terrain/solid_tint"))
        effectUv(CooEffectUvMode.WORLD_XZ)
        uniform("EffectTint", CooUniformValue.Vec3Value(0.1F, 0.85F, 0.35F))
    }

    /** 把场景颜色做径向扰动后直接输出到屏幕的短时后处理示例。 */
    @JvmField
    val HEAT_HAZE = CooShaderEffects.register(id("heat_haze")) {
        fragment(id("post/screen_distortion.fsh"))
        inputSceneColor("scene")
        outputToScreen()
    }

    /**
     * 使用 Cosmos 底图和 Iridescence 扰动图生成星空，并写入共享命名 FBO。
     *
     * 该效果不输出到屏幕；[STARFIELD_BLOCK] 通过 `StarfieldSampler` 读取生成结果。
     */
    @JvmField
    val STARFIELD_FBO = CooShaderEffects.register(id(STARFIELD_FBO_ID)) {
        pass("starfield") {
            fragment(id("post/starfield_fbo.fsh"))
            inputTexture("CosmosSampler", id("test/terrain/cosmos.png"), textureSlot = 0)
            inputTexture("IridescenceSampler", id("test/terrain/iridescence.png"), textureSlot = 1)
            outputToFramebuffer(TerrainFboExampleIds.STARFIELD_TARGET)
        }
    }

    /**
     * 在方块表面按屏幕坐标采样星空 FBO 的 Pipeline。
     *
     * 屏幕空间采样让星空始终朝向观察者，方块转动或观察角度变化时不会带动星空纹理旋转。
     */
    @JvmField
    val STARFIELD_BLOCK = CooPipelines.block(id(STARFIELD_BLOCK_ID)) {
        effectUv(CooEffectUvMode.FACE_LOCAL)
        world {
            shader(id("terrain/starfield_fbo"))
            inputFramebuffer("StarfieldSampler", TerrainFboExampleIds.STARFIELD_TARGET)
        }
    }

    /** 显式运行忽略原纹理的原版方块示例，不在客户端启动时改写任何原版方块。 */
    fun bindVanillaBlockExample(block: Block) {
        CooBlockPipelines.bind(block, SOLID_TINT_BLOCK)
    }

    /** 播放 30 tick 的热扰动屏幕效果。 */
    fun playHeatHaze() = HEAT_HAZE.play {
        duration(30)
        uniform("strength", 0.12F)
        uniform("radius", 0.35F)
    }

    /**
     * 确保星空生成 pass 正在运行，使 [STARFIELD_BLOCK] 每帧都能采样最新 FBO。
     *
     * 方法可在每个客户端 tick 重复调用；已有句柄仍在播放时不会创建重复实例。客户端状态清理后，
     * 下一次调用会自动创建新实例。示例：`RenderPipelineExamples.ensureStarfieldFbo()`。
     */
    fun ensureStarfieldFbo() {
        if (starfieldPlayback?.isPlaying() == true) {
            return
        }
        starfieldPlayback = STARFIELD_FBO.play {
            duration(25_000)
        }
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
