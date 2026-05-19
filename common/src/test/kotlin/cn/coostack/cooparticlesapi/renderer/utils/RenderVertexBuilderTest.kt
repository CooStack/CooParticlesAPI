package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPrimitiveMode
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderVertexBuilderTest {
    @Test
    fun `shape helpers create expected triangle vertex counts`() {
        val builder = RenderVertexBuilder()
            .addSphere(1f, latSegments = 2, lonSegments = 4)
            .addRing(0.5f, 1f, segments = 8)
            .addRibbon(
                listOf(
                    Vector3f(0f, 0f, 0f),
                    Vector3f(1f, 0f, 0f),
                    Vector3f(1f, 1f, 0f)
                ),
                width = 0.2f
            )

        val expectedVertices = 2 * 4 * 6 + 8 * 6 + 2 * 6
        assertEquals(expectedVertices, builder.create().size)

        val model = RenderEntityModelBuilder()
        val pipe = model.pipe("main")
        val primitive = builder.createPrimitives(pipe).single()
        assertEquals(RenderEntityModelPrimitiveMode.TRIANGLES, primitive.primitiveMode)
        assertEquals(expectedVertices, primitive.vertices.size)
    }

    @Test
    fun `line and triangle batches stay separate when added to model`() {
        val model = RenderEntityModelBuilder()
        val pipe = model.pipe("main")

        RenderVertexBuilder()
            .addCircleLine(1f, segments = 4)
            .addQuad(1f, 1f)
            .addTo(model, pipe)

        val primitives = model.build().primitives
        assertEquals(2, primitives.size)
        assertEquals(RenderEntityModelPrimitiveMode.LINES, primitives[0].primitiveMode)
        assertEquals(8, primitives[0].vertices.size)
        assertEquals(RenderEntityModelPrimitiveMode.TRIANGLES, primitives[1].primitiveMode)
        assertEquals(6, primitives[1].vertices.size)
    }

    @Test
    fun `rotate and twist update vertex positions`() {
        val rotated = RenderVertexBuilder()
            .addVertex(1f, 0f, 0f)
            .rotateY(PI / 2.0)
            .create()
            .first()
            .position

        assertEquals(0f, rotated.x, 1.0E-5f)
        assertEquals(0f, rotated.y, 1.0E-5f)
        assertEquals(-1f, rotated.z, 1.0E-5f)

        val twisted = RenderVertexBuilder()
            .addVertex(1f, 1f, 0f)
            .twist(Vector3f(0f, 1f, 0f), PI / 2.0, 0f, 1f)
            .create()
            .first()
            .position

        assertEquals(0f, twisted.x, 1.0E-5f)
        assertEquals(1f, twisted.y, 1.0E-5f)
        assertEquals(-1f, twisted.z, 1.0E-5f)
    }

    @Test
    fun `shader util exposes builder generated vertex data`() {
        val vertices = ShaderUtil.genVertexData {
            addQuad(2f, 2f)
        }

        assertEquals(6, vertices.size)
        assertEquals(-1f, vertices.first().pos.x, 1.0E-5f)
        assertEquals(-1f, vertices.first().pos.y, 1.0E-5f)

        val legacySquare = ShaderUtil.genSquareUVScreen(
            Vector3f(-1f, 1f, 0f),
            Vector3f(1f, 1f, 0f),
            Vector3f(1f, -1f, 0f),
            Vector3f(-1f, -1f, 0f)
        )
        assertEquals(6, legacySquare.size)
        assertEquals(0f, legacySquare.first().uv.x, 1.0E-5f)
        assertEquals(1f, legacySquare.first().uv.y, 1.0E-5f)
    }
}
