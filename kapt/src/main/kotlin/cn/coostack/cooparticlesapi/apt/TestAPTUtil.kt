package cn.coostack.cooparticlesapi.apt
import java.lang.reflect.Modifier

object TestAPTUtil {
    @JvmStatic
    fun print(path: String) {
        println("auto register called $path")
        val clazz = runCatching { Class.forName(path) }
            .getOrNull() ?: return
        clazz.declaredMethods.forEach { method ->
            method.isAccessible = true
            val isStatic = Modifier.isStatic(method.modifiers)
            if (isStatic) {
                method.invoke(null)
            }
        }
    }
}