package cn.coostack.cooparticlesapi.renderer.effects

import net.minecraft.resources.ResourceLocation

/**
 * effectType 到 executor 的注册表。
 * 注册、查询和清理共用对象锁；执行器回调由调用方在锁外执行。
 */
internal object RenderEffectRegistry {
    /** 键为效果类型，值为对应执行器；所有访问都必须持有当前对象的锁。 */
    private val executors = LinkedHashMap<ResourceLocation, RenderEffectExecutor>()

    /**
     * 为某个 effect type 注册执行器。
     */
    @Synchronized
    fun register(effectType: ResourceLocation, executor: RenderEffectExecutor) {
        executors[effectType] = executor
    }

    /**
     * 读取某个 effect type 的执行器。
     */
    @Synchronized
    fun get(effectType: ResourceLocation): RenderEffectExecutor? {
        return executors[effectType]
    }

    /**
     * 清空注册表。
     *
     * 主要用于测试、重载或重新初始化客户端运行时。
     */
    @Synchronized
    fun clear() {
        executors.clear()
    }
}
