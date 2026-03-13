package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object CooShaderReloadListener : SimpleResourceReloadListener<Unit> {
    override fun load(
        p0: ResourceManager,
        p1: ProfilerFiller,
        p2: Executor
    ): CompletableFuture<Unit?> {
        return CompletableFuture.completedFuture(Unit)
    }

    override fun apply(
        p0: Unit,
        p1: ResourceManager,
        p2: ProfilerFiller,
        executor: Executor
    ): CompletableFuture<Void?> {
        return CompletableFuture.runAsync({
            CooShaderReloadSupport.reload(p1)
        }, executor)
    }

    override fun getFabricId(): ResourceLocation? {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "coo_shaders")
    }
}
