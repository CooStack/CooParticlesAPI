package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.platform.services.IPlatformHelper

class ForgePlatformHelper : IPlatformHelper {
    override fun getPlatformName(): String {
        return "Forge"
    }
}
