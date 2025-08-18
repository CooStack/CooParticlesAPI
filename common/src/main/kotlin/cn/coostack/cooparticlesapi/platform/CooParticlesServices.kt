package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.services.IPlatformHelper
import java.util.*
import java.util.function.Supplier

object CooParticlesServices {
    // In this example we provide a platform helper which provides information about what platform the mod is running on.
    // For example this can be used to check if the code is running on Forge vs Fabric, or to ask the modloader if another
    // mod is loaded.
    @JvmField
    val PLATFORM: IPlatformHelper = load(IPlatformHelper::class.java)

    @JvmField
    val CLIENT_NETWORK: ClientNetworking = load(ClientNetworking::class.java)

    @JvmField
    val SERVER_NETWORK: ServerNetworking = load(ServerNetworking::class.java)

    @JvmField
    val API_CONFIG_MANAGER: APIConfigManager = load(APIConfigManager::class.java)

    @JvmField
    val COO_REGISTRY = load(CooRegistry::class.java)

    // This code is used to load a service for the current environment. Your implementation o   f the service must be defined
    // manually by including a text file in META-INF/services named with the fully qualified class name of the service.
    // Inside the file you should write the fully qualified class name of the implementation to load for the platform. For
    // example our file on Forge points to ForgePlatformHelper while Fabric points to FabricPlatformHelper.
    fun <T> load(clazz: Class<T>): T {
        val loadedService = ServiceLoader.load(clazz)
            .findFirst()
            .orElseThrow(Supplier { NullPointerException("Failed to load service for " + clazz.getName()) })
        CooParticlesConstants.logger.debug("Loaded {} for service {}", loadedService, clazz)
        return loadedService!!
    }
}