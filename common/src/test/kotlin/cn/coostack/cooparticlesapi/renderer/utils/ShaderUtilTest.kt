package cn.coostack.cooparticlesapi.renderer.utils

import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderUtilTest {
    @Test
    fun `genPolyBall preserves the faceted mesh layout`() {
        val stacks = 4
        val slices = 8
        val vertices = ShaderUtil.genPolyBall(2F, stacks, slices)

        assertEquals(6 * slices * (stacks + 1), vertices.size)
        assertTrue(vertices.chunked(3).any { triangle ->
            val first = triangle[0].pos
            val second = triangle[1].pos
            val third = triangle[2].pos
            Vector3f(second).sub(first).cross(Vector3f(third).sub(first)).lengthSquared() <= 1.0E-6F
        })
    }

    @Test
    fun `genBall creates a non-degenerate latitude longitude sphere`() {
        val radius = 2F
        val stacks = 4
        val slices = 8
        val vertices = ShaderUtil.genBall(radius, stacks, slices)

        assertEquals(6 * slices * (stacks - 1), vertices.size)
        vertices.chunked(3).forEach { triangle ->
            val first = triangle[0].pos
            val second = triangle[1].pos
            val third = triangle[2].pos
            val normal = Vector3f(second).sub(first).cross(Vector3f(third).sub(first))
            val center = Vector3f(first).add(second).add(third).div(3F)

            assertTrue(normal.lengthSquared() > 1.0E-6F)
            assertTrue(normal.dot(center) > 0F)
            triangle.forEach { vertex ->
                assertEquals(radius, vertex.pos.length(), 1.0E-4F)
            }
        }
    }

    @Test
    fun `genBall rejects invalid dimensions`() {
        assertFailsWith<IllegalArgumentException> { ShaderUtil.genBall(0F, 4, 8) }
        assertFailsWith<IllegalArgumentException> { ShaderUtil.genBall(1F, 1, 8) }
        assertFailsWith<IllegalArgumentException> { ShaderUtil.genBall(1F, 4, 2) }
    }
}
