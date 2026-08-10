package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineIteration
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffects
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

/** APITest 中的屏幕效果全部通过公开的 ShaderEffect Pipeline API 实现。 */
object PostEffectDemoOptions {
    private val GRAYSCALE = CooShaderEffects.register(id("post/demo/grayscale")) {
        fragment(shader("grayscale"))
        inputSceneColor("scene")
        outputToScreen()
    }

    private val SYNCED_SHOCKWAVE = CooShaderEffects.register(id("post/demo/synced_shockwave")) {
        fragment(shader("shockwave"))
        inputSceneColor("scene")
        outputToScreen()
    }

    private val BLOOM = CooShaderEffects.register(id("post/demo/bloom")) {
        val extract = pass("bright_extract") {
            fragment(shader("bloom_bright_extract"))
            inputSceneColor("scene")
        }
        val blur = pingPong("blur", iterations = 4, feedbackSampler = "bright") {
            fragment(shader("bloom_ping_pong_blur"))
            alternate("Axis", 0, 1)
            iterationUniform("Iteration") { iteration: CooPipelineIteration ->
                CooUniformValue.IntValue(iteration.index)
            }
        }
        val composite = pass("composite") {
            fragment(shader("bloom_composite"))
            inputSceneColor("scene")
            input("bright")
            outputToScreen()
        }
        line(extract.color(), blur.input("bright"))
        line(blur.color(), composite.input("bright"))
    }

    private val SCREEN_DISTORTION = CooShaderEffects.register(id("post/demo/screen_distortion")) {
        fragment(shader("screen_distortion"))
        inputSceneColor("scene")
        outputToScreen()
    }

    private val HALO = CooShaderEffects.register(id("post/demo/halo")) {
        val mask = pass("halo_mask") {
            fragment(shader("halo_mask"))
            inputSceneDepth("depth", optional = true)
        }
        val composite = pass("halo_composite") {
            fragment(shader("halo_composite"))
            inputSceneColor("scene")
            input("mask")
            outputToScreen()
        }
        line(mask.color(), composite.input("mask"))
    }

    private val MASK_GRAPH = CooShaderEffects.register(id("post/demo/mask_graph")) {
        val mask = pass("mask") {
            fragment(shader("halo_mask"))
            inputSceneDepth("depth", optional = true)
        }
        val debug = pass("mask_debug") {
            fragment(shader("mask_debug"))
            inputSceneColor("scene")
            input("mask")
            outputToScreen()
        }
        line(mask.color(), debug.input("mask"))
    }

    private val COLOR_SHIFT = CooShaderEffects.register(id("post/demo/color_shift")) {
        fragment(shader("demo_color_shift"))
        inputSceneColor("scene")
        outputToScreen()
    }

    fun init() = Unit

    fun grayscale(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/grayscale",
            testingTick = 80,
            effect = GRAYSCALE,
            configure = {
                uniform("progress", 1F)
            },
            description = "Full-screen grayscale declared and played through CooShaderEffects."
        )
    }

    fun serverShockwave(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/server_synced_shockwave",
            testingTick = 60,
            effect = SYNCED_SHOCKWAVE,
            configure = {
                uniform("center", CooUniformValue.Vec2Value(0.5F, 0.5F))
                uniform("radius", 0.28F)
                uniform("feather", 0.08F)
                uniform("strength", 0.12F)
                uniform("progress", 0.35F)
            },
            description = "Server-triggered screen ShaderEffect using the new play(player) entry."
        )
    }

    fun bloom(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/ping_pong_bloom",
            testingTick = 100,
            effect = BLOOM,
            configure = {
                uniform("threshold", 0.75F)
                uniform("softKnee", 0.45F)
                uniform("blurRadius", 4F)
                uniform("intensity", 1.35F)
                uniform("exposure", 1F)
                uniform("mipLevels", 1)
            },
            description = "Bright extract, four ping-pong blur iterations, then a line-connected composite."
        )
    }

    fun screenDistortion(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/screen_distortion",
            testingTick = 80,
            effect = SCREEN_DISTORTION,
            configure = {
                uniform("strength", 0.055F)
                uniform("progress", 0.3F)
            },
            description = "Single-card ShaderEffect reading the copied scene color."
        )
    }

    fun halo(player: Player): PostEffectDemoOption {
        return haloOption(
            player = player,
            displayName = "shader_effect/depth_aware_halo",
            color = CooUniformValue.Vec4Value(1F, 0.74F, 0.22F, 1F),
            description = "A mask card feeds its FBO output into the halo composite card."
        )
    }

    fun blockBinding(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/mask_graph",
            testingTick = 80,
            effect = MASK_GRAPH,
            configure = {
                uniform("center", CooUniformValue.Vec2Value(0.5F, 0.5F))
                uniform("radius", 0.32F)
                uniform("feather", 0.08F)
                uniform("depthFade", 0.8F)
                uniform("color", CooUniformValue.Vec4Value(0F, 1F, 0.25F, 0.55F))
            },
            description = "Mask generation and mask visualization connected as two Pipeline cards."
        )
    }

    fun itemBinding(player: Player): PostEffectDemoOption {
        return haloOption(
            player = player,
            displayName = "shader_effect/tinted_halo",
            color = CooUniformValue.Vec4Value(0.35F, 0.78F, 1F, 1F),
            description = "The same immutable ShaderEffect template played with an isolated tint snapshot."
        )
    }

    fun customChain(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "shader_effect/custom_line_graph",
            testingTick = 80,
            effect = COLOR_SHIFT,
            configure = {
                uniform("amount", 0.35F)
                uniform("progress", 0.25F)
            },
            description = "Custom screen shader registered and played through the public graph API."
        )
    }

    private fun haloOption(
        player: Player,
        displayName: String,
        color: CooUniformValue.Vec4Value,
        description: String
    ): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = displayName,
            testingTick = 100,
            effect = HALO,
            configure = {
                uniform("center", CooUniformValue.Vec2Value(0.5F, 0.5F))
                uniform("radius", 0.28F)
                uniform("feather", 0.08F)
                uniform("depthFade", 0.8F)
                uniform("color", color)
                uniform("intensity", 1.6F)
            },
            description = description
        )
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }

    private fun shader(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path.fsh")
    }
}
