package cn.coostack.cooparticlesapi.annotations.codec

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.api.DirtyProperty
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * 统一访问普通 codec 字段与 [DirtyProperty] 委托字段。
 */
object CodecFieldAccessor {
    /** 返回类型中参与自动 codec 的普通字段和 dirty 委托字段。 */
    fun fields(type: Class<*>): List<Field> {
        return type.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                    ((field.name.endsWith("\$delegate") &&
                            DirtyProperty::class.java.isAssignableFrom(field.type)) ||
                            (field.isAnnotationPresent(CodecField::class.java) &&
                                    !Modifier.isFinal(field.modifiers)))
        }.sortedBy { it.name }
    }

    /** 返回自动 codec 应用于字段实际值的反射类型。 */
    fun valueType(field: Field): Type {
        if (!DirtyProperty::class.java.isAssignableFrom(field.type)) return field.genericType
        val propertyName = field.name.removeSuffix("\$delegate")
        val capitalizedName = propertyName.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase() else character.toString()
        }
        val getterNames = buildList {
            add("get$capitalizedName")
            if (propertyName.startsWith("is")) add(propertyName)
        }
        val getter = field.declaringClass.declaredMethods.firstOrNull { method ->
            method.parameterCount == 0 && method.name in getterNames
        } ?: throw IllegalArgumentException("无法找到dirty委托字段对应的getter: ${field.name}")
        return getter.genericReturnType
    }

    /** 读取普通字段或 dirty 委托持有的实际值。 */
    fun get(field: Field, owner: Any): Any {
        field.isAccessible = true
        val stored = field.get(owner)
        return if (stored is DirtyProperty<*>) stored.codecValue() else requireNotNull(stored)
    }

    /** 回写普通字段或 dirty 委托持有的实际值，解码本身不会再次置脏。 */
    fun set(field: Field, owner: Any, value: Any) {
        field.isAccessible = true
        val stored = field.get(owner)
        if (stored is DirtyProperty<*>) {
            stored.setCodecValue(value)
        } else {
            field.set(owner, value)
        }
    }
}
