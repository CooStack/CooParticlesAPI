package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.shader.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.uniform.GlUniformProvider
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.*

interface CooShaderProgram {

    enum class GlDrawMod(val gl: Int) {
        POINTS(GL_POINTS),
        LINE_STRIP(GL_LINE_STRIP),
        LINE_LOOP(GL_LINE_LOOP),
        LINE_STRIP_ADJACENCY(GL_LINE_STRIP_ADJACENCY),
        LINES_ADJACENCY(GL_LINES_ADJACENCY),
        TRIANGLE_STRIP(GL_TRIANGLE_STRIP),
        TRIANGLE_FAN(GL_TRIANGLE_FAN),
        TRIANGLES(GL_TRIANGLES),
        TRIANGLE_STRIP_ADJACENCY(GL_TRIANGLE_STRIP_ADJACENCY),
        TRIANGLES_ADJACENCY(GL_TRIANGLES_ADJACENCY),
    }

    var program: Int
    val provides: MutableList<GlUniformProvider<*>>
    val vertexes: MutableList<VertexData>
    val vertexesType: CooVertexFormat
    val vertexShader: GlShader
    val fragmentShader: GlShader

    fun vertex(pos: Vector3f, color: Vector3f, uv: Vector2f, format: CooVertexFormat)
    fun vertex(pos: Vector3f, color: Vector3f)
    fun vertex(pos: Vector3f, uv: Vector2f)
    fun vertex(pos: Vector3f)

    fun init()

    fun draw(drawMode: GlDrawMod)

    fun draw()

    /**
     * 销毁资源
     */
    fun release()

    fun bind()

    fun unbind()

}