package cn.coostack.cooparticlesapi.platform.config

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.config.APIConfig
import cn.coostack.cooparticlesapi.platform.APIConfigManager
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLLoader
import java.nio.file.Files

class NeoForgeAPIConfigManager : APIConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FMLLoader.getGamePath().resolve("config").resolve("${CooParticlesConstants.MOD_ID}.json")

    private var withConfig: APIConfig? = null

    override fun getConfig(): APIConfig {
        return withConfig ?: let {
            loadConfig()
            withConfig!!
        }
    }

    override fun loadConfig() {
        if (!Files.exists(path)) {
            withConfig = APIConfig()
            saveConfig()
            return
        }
        withConfig = gson.fromJson(Files.readString(path), APIConfig::class.java)
    }

    override fun saveConfig() {
        withConfig ?: return
        val json = gson.toJson(withConfig)
        Files.writeString(path, json)
    }
}