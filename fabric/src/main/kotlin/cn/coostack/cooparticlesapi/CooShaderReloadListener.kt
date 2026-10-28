package cn.coostack.cooparticlesapi

import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** 将 Fabric 的异步资源重载通知转交统一 shader 重载入口。 */
object CooShaderReloadListener : SimpleResourceReloadListener<Unit> {
    override fun load(
        p0: ResourceManager,
        p1: Executor
    ): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit)
    }

    override fun apply(
        p0: Unit,
        p1: ResourceManager,
        executor: Executor
    ): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            CooShaderReloadSupport.reload(p1)
        }, executor)
    }

    override fun getFabricId(): Identifier {
        return Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "coo_shaders")
    }
}
