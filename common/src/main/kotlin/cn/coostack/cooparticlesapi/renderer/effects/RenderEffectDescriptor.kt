package cn.coostack.cooparticlesapi.renderer.effects

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import net.minecraft.resources.ResourceLocation

/**
 * RenderEntity V2 当前标准的帧效果描述符。
 *
 * 它只描述“做什么”和“需要什么”，
 * 真正如何执行交给 `RenderEffectRegistry` 中对应 effect type 的 executor。
 */
data class RenderEffectDescriptor(
    /** 该效果的类型 id，用于查找对应 executor。 */
    val effectType: ResourceLocation,
    /** 当前效果实例的逻辑 id。 */
    val effectId: String = effectType.toString(),
    /** 数值越小越先进入执行排序。 */
    val priority: Int = 0,
    /** 提交该效果的来源实例 id。 */
    val sourceInstanceId: String = "",
    /** 当前 descriptor 对 backend 的最低能力要求。 */
    val requiredCapabilities: Set<RenderBackendCapability> = emptySet(),
    /** 具体 effect executor 消费的负载对象。 */
    val payload: Any? = null
)

/**
 * descriptor 风格的效果收集器。
 */
fun interface RenderEffectCollector {
    /**
     * 提交一个 descriptor。
     */
    fun submit(effect: RenderEffectDescriptor)
}

/**
 * 某个 effect type 对应的执行器。
 */
fun interface RenderEffectExecutor {
    /**
     * 批量渲染同一 `effectType` 下的 descriptor 列表。
     */
    fun render(effects: List<RenderEffectDescriptor>)
}
