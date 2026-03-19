package cn.coostack.cooparticlesapi.renderer.backend

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * RenderEntity V2 统一约定的命名场景 target 常量表。
 *
 * 开发者在声明 `requestedSceneTargets` 时，应优先复用这里的 id，
 * 而不是自行拼接字符串。
 */
object RenderSceneTargets {
    /** 主渲染目标，通常代表当前帧的默认输出。 */
    val MAIN: ResourceLocation = id("main")
    /** post 处理专用目标，常作为 frame-post 的主要写入面。 */
    val POST: ResourceLocation = id("post")
    /** 场景颜色副本。 */
    val SCENE_COLOR: ResourceLocation = id("scene_color")
    /** 场景深度副本。 */
    val SCENE_DEPTH: ResourceLocation = id("scene_depth")
    /** bloom 相关的中间目标。 */
    val BLOOM: ResourceLocation = id("bloom")
    /** world light 合成相关目标。 */
    val LIGHT: ResourceLocation = id("light")
    /** 原版半透明层目标。 */
    val TRANSLUCENT_TARGET: ResourceLocation = id("translucent_target")
    /** 原版掉落物 / item entity 相关目标。 */
    val ITEM_ENTITY_TARGET: ResourceLocation = id("item_entity_target")
    /** 原版粒子目标。 */
    val PARTICLES_TARGET: ResourceLocation = id("particles_target")
    /** 原版天气目标。 */
    val WEATHER_TARGET: ResourceLocation = id("weather_target")
    /** 原版云层目标。 */
    val CLOUDS_TARGET: ResourceLocation = id("clouds_target")

    /**
     * 生成当前 mod 命名空间下的 target id。
     */
    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
