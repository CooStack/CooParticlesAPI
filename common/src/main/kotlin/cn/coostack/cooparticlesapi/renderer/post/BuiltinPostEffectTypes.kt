package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation

object BuiltinPostEffectTypes {
    val GRAYSCALE: PostEffectType = CooPostEffectTypes.register(id("grayscale_filter")) {
        screenQuad()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("grayscale", shader("grayscale")) {
            inputSceneColor("scene")
            outputToFinalScreen()
            uniform("progress") { PostEffectParamValue.FloatValue(it.progress) }
        }
        outputToFinalScreen()
    }

    val SCREEN_DISTORTION: PostEffectType = CooPostEffectTypes.register(id("screen_distortion")) {
        maskedScreen()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("distort", shader("screen_distortion")) {
            inputSceneColor("scene")
            inputSceneDepth("depth", optional = true)
            outputToFinalScreen()
            uniform("strength") { it.params["strength"] ?: PostEffectParamValue.FloatValue(0.04f) }
            uniform("progress") { PostEffectParamValue.FloatValue(it.progress) }
        }
        outputToFinalScreen()
    }

    val SHOCKWAVE: PostEffectType = CooPostEffectTypes.register(id("shockwave_filter")) {
        maskedScreen()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("shockwave", shader("shockwave")) {
            inputSceneColor("scene")
            inputSceneDepth("depth", optional = true)
            outputToFinalScreen()
            uniform("radius") { it.params["radius"] ?: PostEffectParamValue.FloatValue(it.progress) }
            uniform("feather") { it.params["feather"] ?: PostEffectParamValue.FloatValue(0.1f) }
            uniform("strength") { it.params["strength"] ?: PostEffectParamValue.FloatValue(0.08f) }
            uniform("progress") { PostEffectParamValue.FloatValue(it.progress) }
        }
        outputToFinalScreen()
    }

    val BLOOM: PostEffectType = CooPostEffectTypes.register(id("bloom")) {
        screenQuad()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        require(RenderBackendCapability.SCENE_COLOR_COPY)
        pass("bright_extract", shader("bloom_bright_extract")) {
            inputSceneColor("scene")
            outputToBloomTarget()
            uniform("threshold") { it.params["threshold"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("softKnee") { it.params["softKnee"] ?: PostEffectParamValue.FloatValue(0.5f) }
        }
        pass("blur_horizontal", shader("bloom_blur_horizontal")) {
            inputBrightColor("bright")
            outputToBloomTarget()
            uniform("blurRadius") { it.params["blurRadius"] ?: PostEffectParamValue.FloatValue(3.0f) }
            uniform("iterations") { it.params["iterations"] ?: PostEffectParamValue.IntValue(1) }
        }
        pass("blur_vertical", shader("bloom_blur_vertical")) {
            inputBrightColor("bright")
            outputToBloomTarget()
            uniform("blurRadius") { it.params["blurRadius"] ?: PostEffectParamValue.FloatValue(3.0f) }
            uniform("iterations") { it.params["iterations"] ?: PostEffectParamValue.IntValue(1) }
        }
        pass("composite", shader("bloom_composite")) {
            inputSceneColor("scene")
            inputBrightColor("bright")
            outputToFinalScreen()
            uniform("intensity") { it.params["intensity"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("mipLevels") {
                it.params["mipLevels"] ?: it.params["mipLevel"] ?: PostEffectParamValue.IntValue(4)
            }
        }
        outputToFinalScreen()
    }

    val HALO: PostEffectType = CooPostEffectTypes.register(id("halo")) {
        worldProjected()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("halo_mask", shader("halo_mask")) {
            inputSceneDepth("depth", optional = true)
            outputToMaskTarget()
            uniform("radius") { it.params["radius"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("feather") { it.params["feather"] ?: PostEffectParamValue.FloatValue(0.25f) }
            uniform("depthFade") { it.params["depthFade"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("throughWalls") { it.params["throughWalls"] ?: PostEffectParamValue.Bool(false) }
        }
        pass("halo_composite", shader("halo_composite")) {
            inputSceneColor("scene")
            inputMask("mask")
            outputToFinalScreen()
            uniform("color") { it.params["color"] ?: PostEffectParamValue.Color(1f, 0.75f, 0.25f, 1f) }
            uniform("intensity") { it.params["intensity"] ?: PostEffectParamValue.FloatValue(1.0f) }
        }
        outputToFinalScreen()
    }

    val MASK_DEBUG: PostEffectType = CooPostEffectTypes.register(id("mask_debug")) {
        maskedScreen()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("mask_debug", shader("mask_debug")) {
            inputSceneColor("scene")
            inputMask("mask", optional = true)
            outputToFinalScreen()
            uniform("color") { it.params["color"] ?: PostEffectParamValue.Color(0f, 1f, 0.2f, 0.6f) }
        }
        outputToFinalScreen()
    }

    fun init() = Unit

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path")
    }

    private fun shader(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path.fsh")
    }
}
