package cn.coostack.cooparticlesapi.renderer.shader.api

import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.data.VertexData

/**
 * 顶点缓冲抽象。
 *
 * 这一层通常封装 VAO/VBO 的创建、绑定、上传与绘制流程。
 */
interface VertexBuffer {
    /**
     * 把当前缓存中的顶点数据重新上传到 GPU。
     */
    fun uploadVertexes()

    /**
     * 设置当前缓冲使用的顶点数据与布局格式。
     */
    fun setVertexes(vertexes: List<VertexData>, format: CooVertexFormat)

    /**
     * 发起一次绘制。
     */
    fun draw()

    /**
     * 初始化 VAO/VBO 等底层资源。
     */
    fun init()

    /**
     * 绑定当前顶点缓冲。
     */
    fun use()

    /**
     * 恢复绑定前的状态。
     */
    fun reset()

    /**
     * 释放 VAO/VBO 等底层资源。
     */
    fun release()
}
