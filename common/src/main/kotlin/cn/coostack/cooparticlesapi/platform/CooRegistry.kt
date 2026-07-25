package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry

interface CooRegistry {
    /**
     * 注册了之后 无需在neo / fabric再次注册
     */
    fun <T : Any> register(registry: CommonDeferredRegistry<T>): CommonDeferredRegistry<T>

    /**
     * 给傻逼的NeoForge 非得要输入你妈的 eventBus准备的
     */
    fun init(any: Any?)

}
