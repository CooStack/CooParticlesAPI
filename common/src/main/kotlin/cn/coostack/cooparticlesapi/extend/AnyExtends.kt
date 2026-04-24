package cn.coostack.cooparticlesapi.extend

import kotlin.reflect.KClass


inline fun <reified T, V> Any.runAsIfType(receiver: T.() -> V?): V? {
    if (this !is T) {
        return null
    }
    return receiver()
}

@Suppress("UNCHECKED_CAST")
inline fun <T : Any, V> Any.runAsIfType(type: KClass<T>, receiver: T.() -> V?): V? {
    if (!type.isInstance(this)) {
        return null
    }
    return receiver(type as T)
}

inline fun <reified T> Any.runAsIfType(receiver: T.() -> Unit) {
    if (this !is T) {
        return
    }
    return receiver()
}