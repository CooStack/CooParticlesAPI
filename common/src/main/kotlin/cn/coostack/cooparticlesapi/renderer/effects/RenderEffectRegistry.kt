package cn.coostack.cooparticlesapi.renderer.effects

import net.minecraft.resources.ResourceLocation

/**
 * effectType 到 executor 的注册表。
 */
object RenderEffectRegistry {
    private val executors = LinkedHashMap<ResourceLocation, RenderEffectExecutor>()

    /**
     * 为某个 effect type 注册执行器。
     */
    fun register(effectType: ResourceLocation, executor: RenderEffectExecutor) {
        executors[effectType] = executor
    }

    /**
     * 读取某个 effect type 的执行器。
     */
    fun get(effectType: ResourceLocation): RenderEffectExecutor? {
        return executors[effectType]
    }

    /**
     * 清空注册表。
     *
     * 主要用于测试、重载或重新初始化客户端运行时。
     */
    fun clear() {
        executors.clear()
    }
}
