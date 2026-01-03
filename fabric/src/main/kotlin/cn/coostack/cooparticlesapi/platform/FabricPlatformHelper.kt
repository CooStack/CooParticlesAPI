package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.platform.services.IPlatformHelper
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader

class FabricPlatformHelper : IPlatformHelper {
    override fun getPlatformName(): String {
        return "Fabric"
    }

    override fun isModLoaded(modId: String): Boolean {
        return FabricLoader.getInstance().isModLoaded(modId)
    }

    override fun isDevelopmentEnvironment(): Boolean {
        return FabricLoader.getInstance().isDevelopmentEnvironment
    }

    override fun getDistType(): DistType {
        return when (FabricLoader.getInstance().environmentType) {
            EnvType.CLIENT -> DistType.CLIENT
            EnvType.SERVER -> DistType.SERVER
        }
    }

    override fun getRenderTypesProvider(): CooRenderTypesProvider {
        return FabricRenderTypesProvider
    }

}