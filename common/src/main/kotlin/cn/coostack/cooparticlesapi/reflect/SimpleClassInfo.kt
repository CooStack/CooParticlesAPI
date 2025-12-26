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

}