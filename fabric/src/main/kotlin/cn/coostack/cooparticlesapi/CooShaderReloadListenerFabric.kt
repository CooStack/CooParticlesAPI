package cn.coostack.cooparticlesapi

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

object CooShaderReloadListenerFabric : SimpleSynchronousResourceReloadListener {
    private val id = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "render_api_reload")

    fun register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
    }

    override fun getFabricId(): ResourceLocation = id

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        CooShaderReloadSupport.reload(resourceManager)
    }
}
