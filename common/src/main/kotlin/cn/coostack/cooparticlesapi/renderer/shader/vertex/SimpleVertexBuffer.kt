package cn.coostack.cooparticlesapi.renderer.shader.vertex

import cn.coostack.cooparticlesapi.renderer.shader.api.VertexBuffer
import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData
import org.lwjgl.opengl.GL33.*
import java.nio.ByteBuffer

class SimpleVertexBuffer : VertexBuffer {
    var vao = 0
        private set
    var vbo = 0
        private set
    var lastVAO = 0
        private set
    var currentVertexFormat = CooVertexFormat.POINT_FORMAT
    var drawMode = GL_TRIANGLES
    private val vertexes = ArrayList<VertexData>()
    private var dirty = false


    /** 当我上传顶点数据时, 就会更新此项目, 如果发现当前的program不符 则会无视dirty继续上传 */
    private var uploadedProgram = 0
    override fun uploadVertexes() {
        val programNow = glGetInteger(GL_CURRENT_PROGRAM)
        val updateProgram = programNow != uploadedProgram
        if (dirty || updateProgram) {
            val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
            var needReset = false
            if (currentVAO != vao) {
                use()
                needReset = true
            }
            upload()
            if (needReset) {
                reset()
            }
            dirty = false
        }
        if (updateProgram) {
            uploadedProgram = programNow
        }
    }

    private fun upload() {
        // 设置 subArray
        val data = vertexToData()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW)
        val offset = dataOffsetCount().toInt() * Float.SIZE_BYTES
        glVertexAttribPointer(0, 3, GL_FLOAT, false, offset, 0L)
        glEnableVertexAttribArray(0)
        if (currentVertexFormat == CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT) {
            glVertexAttribPointer(1, 4, GL_FLOAT, false, offset, 3L * Float.SIZE_BYTES)
            glEnableVertexAttribArray(1)
            glVertexAttribPointer(
                2,
                2,
                GL_FLOAT,
                false,
                offset,
                7L * Float.SIZE_BYTES
            )
            glEnableVertexAttribArray(2)
        } else if (currentVertexFormat != CooVertexFormat.POINT_FORMAT) {
            val size = if (currentVertexFormat == CooVertexFormat.POINT_COLOR_FORMAT) 4 else 2
            glVertexAttribPointer(1, size, GL_FLOAT, false, offset, 3L * Float.SIZE_BYTES)
            glEnableVertexAttribArray(1)
        }
    }

    override fun setVertexes(
        vertexes: List<VertexData>,
        format: CooVertexFormat
    ) {
        this.vertexes.clear()
        this.vertexes.addAll(vertexes)
        currentVertexFormat = format
        dirty = true
    }

    override fun draw() {
        use()
        uploadVertexes()
        // 上传缓冲数据 (在前面设置uniform)
        glDrawArrays(drawMode, 0, vertexes.size)
        reset()
    }

    override fun init() {
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        use()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        reset()
    }

    override fun use() {
        lastVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        glBindVertexArray(vao)
    }

    override fun reset() {
        glBindVertexArray(lastVAO)
    }

    override fun release() {
        glDeleteVertexArrays(vao)
        glDeleteBuffers(vbo)
    }

    private fun dataOffsetCount(): Long = when (currentVertexFormat) {
        CooVertexFormat.POINT_COLOR_FORMAT -> 7
        CooVertexFormat.POINT_TEXTURE_UV_FORMAT -> 5
        CooVertexFormat.POINT_FORMAT -> 3
        CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT -> 9
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
            when (currentVertexFormat) {
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
                    res[i * 7] = pos.x
                    res[i * 7 + 1] = pos.y
                    res[i * 7 + 2] = pos.z
                    res[i * 7 + 3] = color.x
                    res[i * 7 + 4] = color.y
                    res[i * 7 + 5] = color.z
                    res[i * 7 + 6] = color.w

                }

                CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT -> {
                    res[i * 9] = pos.x
                    res[i * 9 + 1] = pos.y
                    res[i * 9 + 2] = pos.z
                    res[i * 9 + 3] = color.x
                    res[i * 9 + 4] = color.y
                    res[i * 9 + 5] = color.z
                    res[i * 9 + 6] = color.w
                    res[i * 9 + 7] = uv.x
                    res[i * 9 + 8] = uv.y
                }

            }
        }
        return res
    }

}