package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.config.APIConfig

interface APIConfigManager {

    fun getConfig(): APIConfig
    fun loadConfig()
    fun saveConfig()
}