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
            .addSphere(1F, latSegments = 2, lonSegments = 4)
            .addRing(0.5F, 1F, segments = 8)
            .addRibbon(
                listOf(
                    Vector3f(0F, 0F, 0F),
                    Vector3f(1F, 0F, 0F),
                    Vector3f(1F, 1F, 0F)
                ),
                width = 0.2F
            )

        val expectedVertices = 2 * 4 * 6 + 8 * 6 + 2 * 6
        assertEquals(expectedVertices, builder.create().size)

        val model = RenderEntityModelBuilder()
        val layer = model.layer("main")
        val primitive = builder.createPrimitives(layer).single()
        assertEquals(RenderEntityModelPrimitiveMode.TRIANGLES, primitive.primitiveMode)
        assertEquals(expectedVertices, primitive.vertices.size)
    }

    @Test
    fun `line and triangle batches stay separate when added to model`() {
        val model = RenderEntityModelBuilder()
        val layer = model.layer("main")

        RenderVertexBuilder()
            .addCircleLine(1F, segments = 4)
            .addQuad(1F, 1F)
            .addTo(model, layer)

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
            .addVertex(1F, 0F, 0F)
            .rotateY(PI / 2.0)
            .create()
            .first()
            .position

        assertEquals(0F, rotated.x, 1.0E-5F)
        assertEquals(0F, rotated.y, 1.0E-5F)
        assertEquals(-1F, rotated.z, 1.0E-5F)

        val twisted = RenderVertexBuilder()
            .addVertex(1F, 1F, 0F)
            .twist(Vector3f(0F, 1F, 0F), PI / 2.0, 0F, 1F)
            .create()
            .first()
            .position

        assertEquals(0F, twisted.x, 1.0E-5F)
        assertEquals(1F, twisted.y, 1.0E-5F)
        assertEquals(-1F, twisted.z, 1.0E-5F)
    }

    @Test
    fun `shader util exposes builder generated vertex data`() {
        val vertices = ShaderUtil.genVertexData {
            addQuad(2F, 2F)
        }

        assertEquals(6, vertices.size)
        assertEquals(-1F, vertices.first().pos.x, 1.0E-5F)
        assertEquals(-1F, vertices.first().pos.y, 1.0E-5F)

        val legacySquare = ShaderUtil.genSquareUVScreen(
            Vector3f(-1F, 1F, 0F),
            Vector3f(1F, 1F, 0F),
            Vector3f(1F, -1F, 0F),
            Vector3f(-1F, -1F, 0F)
        )
        assertEquals(6, legacySquare.size)
        assertEquals(0F, legacySquare.first().uv.x, 1.0E-5F)
        assertEquals(1F, legacySquare.first().uv.y, 1.0E-5F)
    }
}
