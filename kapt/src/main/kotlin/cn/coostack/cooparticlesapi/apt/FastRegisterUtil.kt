package cn.coostack.cooparticlesapi.apt

import java.lang.reflect.Modifier

object FastRegisterUtil {
    const val GENERATE_PACK = "cn.coostack.cooparticlesapi.auto.emitters"
    fun loadAllFastLoader(modID: String) {
        val path = "$GENERATE_PACK.AutoRegister${modID.replace("-", "_")}"
        val clazz = runCatching {
            Class.forName(path)
        }.onFailure {
            it.printStackTrace()
            println("load $path failed")
        }.getOrNull()?: return
        clazz.getMethod("registerAll")
            .invoke(null)
    }
}