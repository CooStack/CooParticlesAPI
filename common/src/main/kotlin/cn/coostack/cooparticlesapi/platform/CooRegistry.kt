package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry

interface CooRegistry {
    fun <T> register(registry: CommonDeferredRegistry<T>): CommonDeferredRegistry<T>

    /**
     * 给傻逼的NeoForge 非得要输入你妈的 eventBus准备的
     */
    fun init(any: Any?)

}