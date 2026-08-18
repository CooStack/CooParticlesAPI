package cn.coostack.cooparticlesapi.renderer.shader

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CooShaderSourceLoaderTest {
    @Test
    fun `unnamespaced coo imports expand from the API include namespace`() {
        val source = ResourceLocation.fromNamespaceAndPath("example", "shaders/core/terrain/main.fsh")
        val include = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "shader/include/fog.glsl")
        val sources = mapOf(
            source to "#version 150\n#coo_import <fog.glsl>\nvoid main() {}",
            include to "float fogFactor() { return 1.0; }"
        )

        assertEquals(
            "#version 150\nfloat fogFactor() { return 1.0; }\nvoid main() {}",
            CooShaderSourceLoader.preprocess(source) { location -> sources.getValue(location) }
        )
    }

    @Test
    fun `namespaced coo imports use the requested mod shader path`() {
        val source = ResourceLocation.fromNamespaceAndPath("example", "shaders/core/terrain/main.fsh")
        val include = ResourceLocation.fromNamespaceAndPath("anothermod", "shader/shared.glsl")
        val sources = mapOf(
            source to "#coo_import <anothermod:shared.glsl>",
            include to "float sharedValue() { return 1.0; }"
        )

        assertEquals(
            "float sharedValue() { return 1.0; }",
            CooShaderSourceLoader.preprocess(source) { location -> sources.getValue(location) }
        )
    }

    @Test
    fun `coo imports reject recursive includes`() {
        val source = ResourceLocation.fromNamespaceAndPath("example", "shaders/core/terrain/main.fsh")
        val include = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "shader/include/shared.glsl")
        val sources = mapOf(
            source to "#coo_import <shared.glsl>",
            include to "#coo_import <shared.glsl>"
        )

        assertFailsWith<IllegalStateException> {
            CooShaderSourceLoader.preprocess(source) { location -> sources.getValue(location) }
        }
    }
}
