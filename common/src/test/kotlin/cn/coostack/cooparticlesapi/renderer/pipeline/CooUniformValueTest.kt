package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CooUniformValueTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun `codec helper exposes the uniform stream codec`() {
        assertSame(CooUniformValue.STREAM_CODEC, CodecHelper.codecOf(CooUniformValue::class.java))
    }

    @Test
    fun `list backed uniform values keep immutable snapshots`() {
        val components = mutableListOf(1, 2)
        val vector = CooUniformValue.IVecValue(components)
        val elements = mutableListOf<CooUniformValue>(vector, CooUniformValue.IVecValue(3, 4))
        val array = CooUniformValue.ArrayValue(elements)
        val originalHash = array.hashCode()

        components += 3
        elements[0] = CooUniformValue.FloatValue(1F)

        assertEquals(listOf(1, 2), vector.components)
        assertEquals(listOf(vector, CooUniformValue.IVecValue(3, 4)), array.elements)
        assertEquals(originalHash, array.hashCode())
    }

    @Test
    fun `uniform shapes reject invalid vectors matrices and arrays`() {
        assertFailsWith<IllegalArgumentException> { CooUniformValue.IVecValue(listOf(1)) }
        assertFailsWith<IllegalArgumentException> { CooUniformValue.MatValue(1, 4, List(4) { 0F }) }
        assertFailsWith<IllegalArgumentException> { CooUniformValue.MatValue(3, 3, List(8) { 0F }) }
        assertFailsWith<IllegalArgumentException> { CooUniformValue.ArrayValue(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            CooUniformValue.ArrayValue(CooUniformValue.FloatValue(1F), CooUniformValue.IntValue(1))
        }
        assertFailsWith<IllegalArgumentException> {
            CooUniformValue.ArrayValue(CooUniformValue.IVecValue(1, 2), CooUniformValue.IVecValue(1, 2, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            CooUniformValue.ArrayValue(
                CooUniformValue.ArrayValue(CooUniformValue.FloatValue(1F))
            )
        }
    }
}
