package cn.coostack.cooparticlesapi.renderer.shader.buffer

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderBufferLayoutTest {
    @AfterTest
    fun tearDown() {
        ShaderBufferRegistry.clear()
    }

    @Test
    fun `builder creates veil style shader buffer layout metadata`() {
        val layout = ShaderBufferLayout.builder<TestData>("OrbData")
            .float("time") { it.time }
            .vec3("color")
            .mat4("transform")
            .build(
                requestedBinding = ShaderBufferBinding.UNIFORM_BUFFER,
                memoryLayout = ShaderBufferMemoryLayout.STD140
            )

        assertEquals("OrbData", layout.name)
        assertEquals(3, layout.fields.size)
        assertEquals(ShaderBufferFieldType.FLOAT, layout.fields[0].type)
        assertEquals(ShaderBufferFieldType.VEC3, layout.fields[1].type)
        assertEquals(ShaderBufferFieldType.MAT4, layout.fields[2].type)
    }

    @Test
    fun `registry assigns bindings separately per buffer binding type`() {
        val uniform = ShaderBufferRegistry.register(
            ShaderBufferLayout.builder<Unit>("UniformBlock")
                .float("time")
                .build(requestedBinding = ShaderBufferBinding.UNIFORM_BUFFER)
        )
        val storage = ShaderBufferRegistry.register(
            ShaderBufferLayout.builder<Unit>("StorageBlock")
                .vec4("values")
                .build(requestedBinding = ShaderBufferBinding.SHADER_STORAGE_BUFFER)
        )

        assertEquals(0, uniform.assignedBinding)
        assertEquals(0, storage.assignedBinding)
    }

    @Test
    fun `glsl block generation falls back to uniform when storage buffers are unsupported`() {
        val layout = ShaderBufferRegistry.register(
            ShaderBufferLayout.builder<Unit>("LightingBlock")
                .vec4("lights")
                .build(
                    requestedBinding = ShaderBufferBinding.SHADER_STORAGE_BUFFER,
                    memoryLayout = ShaderBufferMemoryLayout.STD430
                )
        )

        val storageBlock = layout.createGlslBlock(shaderStorageSupported = true, interfaceName = "LightingData")
        val fallbackBlock = layout.createGlslBlock(shaderStorageSupported = false, interfaceName = "LightingData")

        assertTrue("layout(std430, binding = 0) buffer LightingData" in storageBlock)
        assertTrue("layout(std430, binding = 0) uniform LightingData" in fallbackBlock)
    }

    private data class TestData(
        val time: Float
    )
}
