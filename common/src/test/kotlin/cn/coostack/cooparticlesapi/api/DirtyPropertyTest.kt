package cn.coostack.cooparticlesapi.api

import cn.coostack.cooparticlesapi.annotations.codec.CodecFieldAccessor
import java.lang.reflect.ParameterizedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirtyPropertyTest {
    @Test
    fun `changed assignment marks owner dirty once`() {
        val owner = TestOwner()

        owner.value = 1
        owner.value = 2
        owner.value = 2

        assertEquals(1, owner.dirtyCount)
        assertEquals(2, owner.value)
    }

    @Test
    fun `codec update replaces value without marking owner dirty`() {
        val owner = TestOwner()
        val field = owner.javaClass.declaredFields.single {
            it.name == "value\$delegate"
        }

        CodecFieldAccessor.set(field, owner, 3)

        assertEquals(0, owner.dirtyCount)
        assertEquals(3, owner.value)
    }

    @Test
    fun `codec helper exposes delegated value type and value`() {
        val owner = TestOwner()
        val fields = CodecFieldAccessor.fields(owner.javaClass).associateBy { it.name }
        val valueField = requireNotNull(fields["value\$delegate"])
        val valuesField = requireNotNull(fields["values\$delegate"])
        val valuesType = CodecFieldAccessor.valueType(valuesField) as ParameterizedType

        assertEquals(Int::class.java, CodecFieldAccessor.valueType(valueField))
        assertEquals(1, CodecFieldAccessor.get(valueField, owner))
        assertEquals(String::class.java, valuesType.actualTypeArguments.single())
        assertTrue(fields.keys.all { it.endsWith("\$delegate") })
    }

    private class TestOwner : NetworkDirtyMarkable {
        var dirtyCount = 0
        var value by dirty(1)
        var values by dirty(listOf("initial"))

        override fun markDirty() {
            dirtyCount++
        }
    }
}
