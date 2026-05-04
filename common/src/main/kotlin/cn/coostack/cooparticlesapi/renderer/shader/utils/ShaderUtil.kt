package cn.coostack.cooparticlesapi.renderer.shader.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.utils.RenderVertexBuilder
import org.joml.Vector3f

/**
 * Compatibility facade for the old shader utility package.
 *
 * New code should import [cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil].
 */
@Deprecated(
    message = "Use cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil",
    replaceWith = ReplaceWith("ShaderUtil", "cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil")
)
object ShaderUtil {
    fun vertexBuilder(block: RenderVertexBuilder.() -> Unit = {}): RenderVertexBuilder =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.vertexBuilder(block)

    fun genVertexData(block: RenderVertexBuilder.() -> Unit): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genVertexData(block)

    fun genModelVertices(block: RenderVertexBuilder.() -> Unit): List<RenderEntityModelVertex> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genModelVertices(block)

    fun genSquare(w: Float, h: Float): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genSquare(w, h)

    fun genSquare(p1: Vector3f, p2: Vector3f): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genSquare(p1, p2)

    fun genCylinder(r: Float, tessellate: Float, height: Float): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genCylinder(r, tessellate, height)

    fun genSquareUV(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genSquareUV(p1, p2, p3, p4)

    fun genSquareUVScreen(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genSquareUVScreen(p1, p2, p3, p4)

    fun genSquare(p1: Vector3f, p2: Vector3f, p3: Vector3f, p4: Vector3f): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genSquare(p1, p2, p3, p4)

    fun genBox(): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genBox()

    fun genBall(r: Float, stacks: Int, slices: Int): List<VertexData> =
        cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil.genBall(r, stacks, slices)
}
