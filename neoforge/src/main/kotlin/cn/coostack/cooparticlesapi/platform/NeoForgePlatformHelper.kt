package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.platform.services.IPlatformHelper
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader

class NeoForgePlatformHelper : IPlatformHelper {
    override fun getPlatformName(): String {
        return "NeoForge"
    }

    override fun isModLoaded(modId: String): Boolean {
        return ModList.get().isLoaded(modId)
    }

    override fun isDevelopmentEnvironment(): Boolean {
        return !FMLLoader.isProduction()
    }

    override fun getDistType(): DistType {
        return when (FMLLoader.getDist()) {
            Dist.CLIENT -> DistType.CLIENT
            Dist.DEDICATED_SERVER -> DistType.SERVER
        }
    }
}