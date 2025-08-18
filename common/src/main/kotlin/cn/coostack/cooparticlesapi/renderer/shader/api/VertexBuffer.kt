package cn.coostack.cooparticlesapi.renderer.shader.api

import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData

/**
 * VAO
 * VBO
 * 数据在这里使用
 */
interface VertexBuffer {
    /**
     * 修改顶点数据
     */
    fun uploadVertexes()

    fun setVertexes(vertexes: List<VertexData>, format: CooVertexFormat)

    fun draw()

    /**
     * 初始化vao 和 vbo
     */
    fun init()

    /**
     * 绑定对应的 vao 和 vbo
     */
    fun use()

    /**
     * 重置回使用前的 vao 和 vbo
     */
    fun reset()

    /**
     * 删除这个vao 和 vbo
     */
    fun release()
}