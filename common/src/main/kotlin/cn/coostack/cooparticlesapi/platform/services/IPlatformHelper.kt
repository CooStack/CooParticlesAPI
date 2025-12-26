package cn.coostack.cooparticlesapi.platform.services

import cn.coostack.cooparticlesapi.enums.DistType

interface IPlatformHelper {
    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    fun getPlatformName(): String?

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    fun isModLoaded(modId: String): Boolean

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    fun isDevelopmentEnvironment(): Boolean
    fun getDistType(): DistType

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    fun getEnvironmentName(): String {
        return if (isDevelopmentEnvironment()) "development" else "production"
    }


}