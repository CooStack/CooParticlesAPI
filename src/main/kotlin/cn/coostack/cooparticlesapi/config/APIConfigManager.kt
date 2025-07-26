package cn.coostack.cooparticlesapi.config

import cn.coostack.cooparticlesapi.CooParticleAPI
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files


object APIConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("${CooParticleAPI.MOD_ID}.json")

    @JvmStatic
    private var withConfig: APIConfig? = null

    @JvmStatic
    fun getConfig(): APIConfig {
        return withConfig ?: let {
            loadConfig()
            withConfig!!
        }
    }

    @JvmStatic
    fun loadConfig() {
        if (!Files.exists(path)) {
            withConfig = APIConfig()
            saveConfig()
            return
        }
        withConfig = gson.fromJson(Files.readString(path), APIConfig::class.java)
    }

    @JvmStatic
    fun saveConfig() {
        withConfig ?: return
        val json = gson.toJson(withConfig)
        Files.writeString(path, json)
    }

}