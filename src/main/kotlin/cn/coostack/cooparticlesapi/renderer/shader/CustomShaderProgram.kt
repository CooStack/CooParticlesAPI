package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.shader.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.uniform.GlUniformProvider
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL33.*

class CustomShaderProgram(
    override val vertexShader: GlShader,
    override val fragmentShader: GlShader,
    override val vertexesType: CooVertexFormat
) :
    CooShaderProgram {
    override var program: Int = 0
    override val provides: MutableList<GlUniformProvider<*>> = ArrayList()
    override val vertexes: MutableList<VertexData> = ArrayList()
    var arrayBufferUsage = GlBufferUsage.GL_STATIC_DRAW
    var defaultDrawMod = CooShaderProgram.GlDrawMod.TRIANGLES
    private var vbo: Int = 0
    private var vao: Int = 0
    override fun vertex(
        pos: Vector3f,
        color: Vector3f,
        uv: Vector2f,
        format: CooVertexFormat
    ) {
        vertexes.add(VertexData(pos, color, uv, format))
    }

    override fun vertex(pos: Vector3f, color: Vector3f) {
        vertex(pos, color, Vector2f(), CooVertexFormat.POINT_COLOR_FORMAT)
    }

    override fun vertex(pos: Vector3f, uv: Vector2f) {
        vertex(pos, Vector3f(), uv, CooVertexFormat.POINT_TEXTURE_UV_FORMAT)
    }

    override fun vertex(pos: Vector3f) {
        vertex(pos, Vector3f(), Vector2f(), CooVertexFormat.POINT_FORMAT)
    }

    override fun init() {
        program = glCreateProgram()
        vertexShader.init()
        fragmentShader.init()
        glAttachShader(program, vertexShader.shaderID())
        glAttachShader(program, fragmentShader.shaderID())
        glLinkProgram(program)
        assertsProgram()

        // 绑定创建缓冲区
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        bind()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, vertexToData(), arrayBufferUsage.gl)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, dataOffsetCount().toInt() * Float.SIZE_BYTES, 0)
        glEnableVertexAttribArray(0)

        if (vertexesType == CooVertexFormat.POINT_COLOR_FORMAT || vertexesType == CooVertexFormat.POINT_TEXTURE_UV_FORMAT || vertexesType == CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT) {
            glVertexAttribPointer(
                1,
                3,
                GL_FLOAT,
                false,
                dataOffsetCount().toInt() * Float.SIZE_BYTES,
                if (vertexesType == CooVertexFormat.POINT_TEXTURE_UV_FORMAT) 2L else 3L * Float.SIZE_BYTES
            )
            glEnableVertexAttribArray(1)
        }

        if (vertexesType == CooVertexFormat.POINT_TEXTURE_UV_FORMAT) {
            glVertexAttribPointer(
                2, 3, GL_FLOAT, false, dataOffsetCount().toInt() * Float.SIZE_BYTES,
                6L * Float.SIZE_BYTES
            )
            glEnableVertexAttribArray(2)
        }

        glDeleteShader(vertexShader.shaderID())
        glDeleteShader(fragmentShader.shaderID())
        unbind()
    }

    override fun draw(drawMode: CooShaderProgram.GlDrawMod) {
        bind()
        provides.forEach {
            it.provide(this)
        }
        glDrawArrays(drawMode.gl, 0, vertexes.size)
        unbind()
    }

    override fun draw() {
        draw(defaultDrawMod)
    }

    override fun bind() {
        glUseProgram(program)
        glBindVertexArray(vao)
    }

    override fun unbind() {
        glBindVertexArray(0)
        glUseProgram(0)
    }

    private fun assertsProgram() {
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            throw RuntimeException("link shader program error ${glGetProgramInfoLog(program)}")
        }
    }

    override fun release() {
    }

    private fun dataOffsetCount(): Long = when (vertexesType) {
        CooVertexFormat.POINT_COLOR_FORMAT -> 6
        CooVertexFormat.POINT_TEXTURE_UV_FORMAT -> 5
        CooVertexFormat.POINT_FORMAT -> 3
        CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT -> 8
    }

    private fun vertexToData(): FloatArray {
        var count = vertexes.size
        if (count <= 0) return FloatArray(0)
        count *= dataOffsetCount().toInt()
        val res = FloatArray(count)
        for (i in vertexes.indices) {
            val vertex = vertexes[i]
            val pos = vertex.pos
            val color = vertex.color
            val uv = vertex.uv
            when (vertexesType) {
                CooVertexFormat.POINT_TEXTURE_UV_FORMAT -> {
                    res[i * 5] = pos.x
                    res[i * 5 + 1] = pos.y
                    res[i * 5 + 2] = pos.z
                    res[i * 5 + 3] = uv.x
                    res[i * 5 + 4] = uv.y
                }

                CooVertexFormat.POINT_FORMAT -> {
                    res[i * 3] = pos.x
                    res[i * 3 + 1] = pos.y
                    res[i * 3 + 2] = pos.z
                }

                CooVertexFormat.POINT_COLOR_FORMAT -> {
                    res[i * 6] = pos.x
                    res[i * 6 + 1] = pos.y
                    res[i * 6 + 2] = pos.z
                    res[i * 6 + 3] = color.x
                    res[i * 6 + 4] = color.y
                    res[i * 6 + 5] = color.z

                }

                CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT -> {
                    res[i * 8] = pos.x
                    res[i * 8 + 1] = pos.y
                    res[i * 8 + 2] = pos.z
                    res[i * 8 + 3] = color.x
                    res[i * 8 + 4] = color.y
                    res[i * 8 + 5] = color.z
                    res[i * 8 + 6] = uv.x
                    res[i * 8 + 7] = uv.y
                }

            }
        }
        return res
    }

}