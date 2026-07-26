package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation

/**
 * 框架随包提供的 post effect 类型集合。
 *
 * 这些类型既是可直接使用的效果，也是自定义 post 的参考模板：
 *
 * - 简单单 pass：参考 [GRAYSCALE]
 * - 读取深度但允许降级：参考 [SCREEN_DISTORTION]、[SHOCKWAVE]
 * - 多 pass 中间 target：参考 [BLOOM]、[HALO]
 * - mask 调试：参考 [MASK_DEBUG]
 *
 * 这里替代的是旧式“每个效果自己手写 FBO、sampler、uniform、执行顺序”的注册代码。
 * 业务方只需要声明 chain，实际执行由 [PostEffectFrameExecutor] 和客户端 backend 完成。
 */
object BuiltinPostEffectTypes {
    /**
     * 灰度滤镜。
     *
     * 输入 `scene` 读取当前场景颜色，输出到最终屏幕。
     * 常用参数：无；内置上传 `progress`，shader 可用它做渐入。
     */
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

    /**
     * 屏幕扰动效果。
     *
     * 输入：
     *
     * - `scene`：场景颜色，必需
     * - `depth`：场景深度，可选；没有深度能力时仍可运行，只是 shader 不能做深度约束
     *
     * 常用参数：
     *
     * - `strength: FloatValue`：扰动强度，默认 `0.04`
     */
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

    /**
     * 以绑定点为中心的冲击波。
     *
     * 常用于 `bindWorld`、`bindEntity`、`bindBlock`，backend 会把绑定点投影为 `center` uniform。
     *
     * 常用参数：
     *
     * - `radius: FloatValue`：波纹半径，默认使用生命周期 `progress`
     * - `feather: FloatValue`：边缘宽度，默认 `0.1`
     * - `strength: FloatValue`：折射强度，默认 `0.08`
     */
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

    /**
     * Bloom 泛光。
     *
     * 声明上只有 bright extract / blur / composite 三类 pass，但 executor 会按 `mipLevels`
     * 和 `iterations` 展开为多级 downsample、blur、upsample。自定义泛光通常不需要手动写这些中间 pass。
     *
     * 常用参数：
     *
     * - `threshold: FloatValue`：亮度阈值，默认 `1.0`
     * - `softKnee: FloatValue`：阈值软化，默认 `0.5`
     * - `blurRadius: FloatValue`：模糊半径，默认 `3.0`
     * - `iterations: IntValue`：每级 blur 次数，默认 `1`
     * - `mipLevels: IntValue`：mip 层数，默认 `4`
     * - `intensity: FloatValue`：合成强度，默认 `1.0`
     * - `exposure: FloatValue`：曝光补偿倍率，默认 `1.0`（即关闭）
     */
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
            uniform("exposure") { it.params["exposure"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("mipLevels") {
                it.params["mipLevels"] ?: it.params["mipLevel"] ?: PostEffectParamValue.IntValue(4)
            }
        }
        outputToFinalScreen()
    }

    /**
     * 世界投影光环。
     *
     * 第一 pass 输出 mask，第二 pass 读取 `scene + mask` 合成。适合实体、方块、世界坐标附近的后处理高亮。
     *
     * 常用参数：
     *
     * - `radius: FloatValue`：屏幕空间半径，默认 `1.0`
     * - `feather: FloatValue`：边缘宽度，默认 `0.25`
     * - `depthFade: FloatValue`：深度衰减，默认 `1.0`
     * - `throughWalls: BoolValue`：是否允许穿墙显示，默认 `false`
     * - `color: ColorValue`：合成颜色，默认金色
     * - `intensity: FloatValue`：合成强度，默认 `1.0`
     */
    val HALO: PostEffectType = CooPostEffectTypes.register(id("halo")) {
        worldProjected()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("halo_mask", shader("halo_mask")) {
            inputSceneDepth("depth", optional = true)
            outputToMaskTarget()
            uniform("radius") { it.params["radius"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("feather") { it.params["feather"] ?: PostEffectParamValue.FloatValue(0.25f) }
            uniform("depthFade") { it.params["depthFade"] ?: PostEffectParamValue.FloatValue(1.0f) }
            uniform("throughWalls") { it.params["throughWalls"] ?: PostEffectParamValue.BoolValue(false) }
        }
        pass("halo_composite", shader("halo_composite")) {
            inputSceneColor("scene")
            inputMask("mask")
            outputToFinalScreen()
            uniform("color") { it.params["color"] ?: PostEffectParamValue.ColorValue(1f, 0.75f, 0.25f, 1f) }
            uniform("intensity") { it.params["intensity"] ?: PostEffectParamValue.FloatValue(1.0f) }
        }
        outputToFinalScreen()
    }

    /**
     * mask 可视化调试效果。
     *
     * 用于检查 `outputToMaskTarget()` 生成的 mask 是否符合预期。`mask` 输入是可选的，
     * 因此即使没有上游 mask pass 也不会让整帧执行失败。
     */
    val MASK_DEBUG: PostEffectType = CooPostEffectTypes.register(id("mask_debug")) {
        maskedScreen()
        require(RenderBackendCapability.FINAL_FRAME_POST)
        pass("mask_debug", shader("mask_debug")) {
            inputSceneColor("scene")
            inputMask("mask", optional = true)
            outputToFinalScreen()
            uniform("color") { it.params["color"] ?: PostEffectParamValue.ColorValue(0f, 1f, 0.2f, 0.6f) }
        }
        outputToFinalScreen()
    }

    /** 触发 object 初始化，确保所有内置类型完成注册。 */
    fun init() = Unit

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path")
    }

    private fun shader(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path.fsh")
    }
}
