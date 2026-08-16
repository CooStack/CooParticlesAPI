package cn.coostack.cooparticlesapi.api

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 表示对象支持在同步字段变化后标记完整网络状态。
 */
interface NetworkDirtyMarkable {
    /** 标记完整网络状态需要重新同步。 */
    fun markDirty()

    /**
     * 创建一个在值变化时自动调用 [markDirty] 的属性委托。
     *
     * Example: `var color by dirty(initialColor)`。
     * ParticleComposition、DisplayEntity 和 ParticleEmitters 的自动 codec 会直接同步该字段，
     * 不需要再添加 `@CodecField`。
     * 对可变对象内部的修改不会触发赋值，调用方需要重新赋值或手动调用 [markDirty]。
     *
     * @param initial 初始字段值
     * @return 自动标记完整同步状态的属性委托
     */
    fun <T : Any> dirty(initial: T): DirtyProperty<T> {
        return DirtyProperty(initial, ::markDirty)
    }
}

/**
 * 在属性被赋予不同值时通知所属同步对象的 Kotlin 属性委托。
 *
 * 该类型同时作为自动 codec 字段的存储容器，由 CodecHelper 读取和回写实际值。
 *
 * @param T 字段值类型
 * @param initial 初始字段值
 * @param onDirty 字段变化后的通知函数
 */
class DirtyProperty<T : Any> internal constructor(
    initial: T,
    private val onDirty: () -> Unit,
) : ReadWriteProperty<Any?, T> {
    private var value = initial

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (this.value == value) return
        this.value = value
        onDirty()
    }

    internal fun codecValue(): T {
        return value
    }

    @Suppress("UNCHECKED_CAST")
    internal fun setCodecValue(value: Any) {
        this.value = value as T
    }
}
