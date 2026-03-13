package cn.coostack.cooparticlesapi

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

object CooShaderReloadListenerNeo : SimplePreparableReloadListener<Unit>() {
    override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller): Unit = Unit

    override fun apply(object_: Unit, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        CooShaderReloadSupport.reload(resourceManager)
    }
}
