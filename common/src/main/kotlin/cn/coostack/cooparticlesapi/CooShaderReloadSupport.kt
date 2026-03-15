package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import net.minecraft.server.packs.resources.ResourceManager

object CooShaderReloadSupport {
    @JvmStatic
    fun reload(resourceManager: ResourceManager) {
        // Legacy V1 renderer demo reload hooks are disabled during the V2 migration.
        MCShaders.init(resourceManager)
        CooParticlesAPIClient.reloadShaderPrograms()
    }
}
