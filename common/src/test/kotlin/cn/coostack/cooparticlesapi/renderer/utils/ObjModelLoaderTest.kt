package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitiveMode
import org.joml.Vector4f
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObjModelLoaderTest {
    @Test
    fun `parse triangle with uv normal and vertex color`() {
        val vertices = ObjModelLoader.parse(
            """
            v 0 0 0 1 0 0 0.5
            v 1 0 0
            v 0 1 0
            vt 0 0
            vt 1 0
            vt 0 1
            vn 0 0 1
            f 1/1/1 2/2/1 3/3/1
            """.trimIndent(),
            color = Vector4f(0.2f, 0.3f, 0.4f, 0.8f)
        ).create()

        assertEquals(3, vertices.size)
        assertEquals(1f, vertices[0].color.x, 1.0E-5f)
        assertEquals(0.5f, vertices[0].color.w, 1.0E-5f)
        assertEquals(0.2f, vertices[1].color.x, 1.0E-5f)
        assertEquals(1f, vertices[2].uv.y, 1.0E-5f)
        assertEquals(1f, vertices[0].normal.z, 1.0E-5f)
    }

    @Test
    fun `quad faces are triangulated and negative indices resolve from the end`() {
        val vertices = ObjModelLoader.parse(
            """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            f -4 -3 -2 -1
            """.trimIndent()
        ).create()

        assertEquals(6, vertices.size)
        assertEquals(0f, vertices[0].position.x, 1.0E-5f)
        assertEquals(0f, vertices[0].position.y, 1.0E-5f)
        assertEquals(1f, vertices[2].position.x, 1.0E-5f)
        assertEquals(1f, vertices[2].position.y, 1.0E-5f)
        assertEquals(0f, vertices[5].position.x, 1.0E-5f)
        assertEquals(1f, vertices[5].position.y, 1.0E-5f)
    }

    @Test
    fun `missing normals use computed face normal`() {
        val vertices = ObjModelLoader.parse(
            """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """.trimIndent()
        ).create()

        assertEquals(1f, vertices[0].normal.z, 1.0E-5f)
        assertEquals(1f, vertices[1].normal.z, 1.0E-5f)
        assertEquals(1f, vertices[2].normal.z, 1.0E-5f)
    }

    @Test
    fun `resource loader builds model primitives`() {
        val builder = RenderEntityModelBuilder()
        val pipe = builder.pipe("obj")
        val model = ObjModelLoader.buildModel(
            CooParticlesConstants.MOD_ID,
            "models/obj/test_quad.obj",
            pipe,
            classLoader = testResourceClassLoader()
        )

        val primitive = model.primitives.single()
        assertEquals(RenderEntityModelPrimitiveMode.TRIANGLES, primitive.primitiveMode)
        assertEquals(6, primitive.vertices.size)
    }

    @Test
    fun `invalid face indices include line number`() {
        val error = assertFailsWith<IllegalStateException> {
            ObjModelLoader.parse(
                """
                v 0 0 0
                v 1 0 0
                f 1 2 3
                """.trimIndent()
            )
        }

        assertEquals("Invalid OBJ at line 3: position index 3 is out of bounds", error.message)
    }

    private fun testResourceClassLoader(): ClassLoader {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val resourceRoot = listOf(
            workingDirectory.resolve("common/src/test/resources"),
            workingDirectory.resolve("src/test/resources")
        ).first { Files.exists(it) }
        return URLClassLoader(arrayOf(resourceRoot.toUri().toURL()), ObjModelLoader::class.java.classLoader)
    }
}
