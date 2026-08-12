package cn.coostack.cooparticlesapi.reflect

/**
 * 防止Class.forName导致他妈的NF崩溃无法启动的问题
 *
 * @property type
 * @property annotations
 */
class SimpleClassInfo(val type: String, val annotations: HashSet<String>) {
    fun isAnnotationPresent(anno: Class<out Annotation>): Boolean {
        return anno.name in annotations
    }

    fun toClass(): Class<*> {
        return Class.forName(type)
    }

    /**
     * 按需加载扫描到的类，并允许调用方避免执行静态初始化。
     *
     * 客户端 renderer 扫描使用 `initialize = false`，防止注册阶段提前创建渲染资源。
     */
    fun toClass(initialize: Boolean): Class<*> {
        return Class.forName(type, initialize, SimpleClassInfo::class.java.classLoader)
    }

}
