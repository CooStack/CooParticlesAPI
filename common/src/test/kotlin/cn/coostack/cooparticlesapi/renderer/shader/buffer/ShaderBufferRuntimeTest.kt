package cn.coostack.cooparticlesapi.renderer.shader.buffer

import org.joml.Vector3f
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderBufferRuntimeTest {
    @AfterTest
    fun tearDown() {
        ShaderBufferCache.releaseAll()
        ShaderBufferRegistry.clear()
    }

    @Test
    fun `layout computes aligned byte size for std140 fields`() {
        val layout = ShaderBufferLayout.builder<TestData>("OrbData")
            .float("time") { it.time }
            .vec3("color") { it.color }
            .build(
                requestedBinding = ShaderBufferBinding.UNIFORM_BUFFER,
                memoryLayout = ShaderBufferMemoryLayout.STD140
            )

        assertEquals(32, layout.byteSize())
    }

    @Test
    fun `layout encodes float and vec3 payloads into native buffer`() {
        val layout = ShaderBufferLayout.builder<TestData>("OrbData")
            .float("time") { it.time }
            .vec3("color") { it.color }
            .build()

        val encoded = layout.encode(TestData(2.5f, Vector3f(1f, 2f, 3f)))

        assertEquals(32, encoded.remaining())
        assertEquals(2.5f, encoded.getFloat(0))
        assertEquals(1f, encoded.getFloat(16))
        assertEquals(2f, encoded.getFloat(20))
        assertEquals(3f, encoded.getFloat(24))
    }

    @Test
    fun `buffer cache returns one object per registered layout`() {
        val layout = ShaderBufferLayout.builder<Unit>("UniformBlock")
            .float("time") { 1f }
            .build()

        val first = ShaderBufferCache.getOrCreate(layout)
        val second = ShaderBufferCache.getOrCreate(layout)

        assertTrue(first === second)
        assertEquals(0, first.layout.assignedBinding)
        assertEquals(1, ShaderBufferRegistry.bindingCount(ShaderBufferBinding.UNIFORM_BUFFER))
    }

    @Test
    fun `buffer cache can release targeted layouts without clearing unrelated buffers`() {
        val affectedLayout = ShaderBufferLayout.builder<Unit>("AffectedBlock")
            .float("time") { 1f }
            .build()
        val retainedLayout = ShaderBufferLayout.builder<Unit>("RetainedBlock")
            .float("time") { 2f }
            .build()

        val affected = ShaderBufferCache.getOrCreate(affectedLayout)
        val retained = ShaderBufferCache.getOrCreate(retainedLayout)

        val released = ShaderBufferCache.releaseLayouts(listOf(affectedLayout))

        assertEquals(1, released)
        assertTrue(ShaderBufferCache.getOrCreate(affectedLayout) !== affected)
        assertTrue(ShaderBufferCache.getOrCreate(retainedLayout) === retained)
    }

    private data class TestData(
        val time: Float,
        val color: Vector3f
    )
}
