package cn.coostack.cooparticlesapi

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/** 在客户端资源重载时更新统一渲染资源。 */
object CooShaderReloadListenerFabric : SimpleSynchronousResourceReloadListener {

    fun register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
    }

    override fun getFabricId(): Identifier =
        Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "render_api_reload")

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        CooShaderReloadSupport.reload(resourceManager)
    }
}
