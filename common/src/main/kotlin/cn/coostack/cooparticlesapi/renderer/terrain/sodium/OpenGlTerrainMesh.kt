package cn.coostack.cooparticlesapi.renderer.terrain.sodium

import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.backend.opengl.GlStateManager
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * 拥有 Sodium 覆盖层的 OpenGL VAO、顶点与索引缓冲，不拥有 shader、目标或原始 CPU 网格。
 * 仅允许 OpenGL 后端的渲染线程创建、重排、绘制和释放；整数 UV 保留有符号 16 位解释。
 *
 * @param mesh 已完成构建及可选透明排序的四边形网格，构造期间复制其数据，调用方仍负责关闭
 */
internal class OpenGlTerrainMesh(mesh: MeshData) : AutoCloseable {
    /** 只读顶点布局；每次 shader 改变时按实际属性位置配置。 */
    private val format = mesh.drawState().format()
    /** 固定索引数量，透明重排不改变数量。 */
    private val indexCount = mesh.drawState().indexCount()
    /** 原版为当前顶点数量选择的索引宽度。 */
    private val indexType = mesh.drawState().indexType()
    /** 本网格拥有的 VAO，零表示尚未初始化或已释放。 */
    private var vertexArray = 0
    /** 本网格拥有的不可变顶点缓冲。 */
    private var vertexBuffer = 0
    /** 本网格拥有的可替换索引缓冲。 */
    private var indexBuffer = 0
    /** 上次配置属性布局的实际 GL 程序；零表示尚未配置。 */
    private var configuredProgram = 0
    /** 当前 VAO 已启用的属性位置，切换程序时先禁用旧位置。 */
    private val enabledAttributes = mutableListOf<Int>()
    /** 已关闭的批次不得再次排队绘制。 */
    val isClosed: Boolean get() = vertexArray == 0

    init {
        OpenGlRenderTargetBridge.requireOpenGlBackend()
        require(mesh.drawState().primitiveTopology() == PrimitiveTopology.QUADS) { "地形覆盖网格必须使用四边形拓扑" }
        require(mesh.drawState().vertexCount() % 4 == 0) { "地形覆盖网格存在不完整的四边形" }
        require(indexCount == mesh.drawState().vertexCount() / 4 * 6) { "地形覆盖网格索引数量不匹配" }
        try {
            vertexArray = glGenVertexArrays()
            vertexBuffer = glGenBuffers()
            indexBuffer = glGenBuffers()
            check(vertexArray != 0 && vertexBuffer != 0 && indexBuffer != 0) { "无法分配地形覆盖 GPU 缓冲" }
            withBound {
                glBufferData(GL_ARRAY_BUFFER, mesh.vertexBuffer(), GL_STATIC_DRAW)
                val indices = mesh.indexBuffer()
                if (indices != null) {
                    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_DYNAMIC_DRAW)
                } else {
                    sequentialIndices(mesh.drawState().vertexCount()).let { generated ->
                        try {
                            glBufferData(GL_ELEMENT_ARRAY_BUFFER, generated, GL_DYNAMIC_DRAW)
                        } finally {
                            MemoryUtil.memFree(generated)
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    /**
     * 替换同一网格的透明排序索引，不触碰顶点或 CPU 缓冲的所有权。
     * 示例：`mesh.uploadIndexBuffer(sorted.byteBuffer())`。
     * @param indices 与构造时索引宽度、数量一致的只读输入，方法返回后不再引用
     */
    fun uploadIndexBuffer(indices: ByteBuffer) {
        check(!isClosed) { "地形覆盖网格已释放" }
        require(indices.remaining() == indexCount * indexType.bytes) { "地形覆盖排序索引长度不匹配" }
        withBound { glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_DYNAMIC_DRAW) }
    }

    /**
     * 使用调用方已经绑定的地形 shader 绘制；矩阵、材质、光照、FBO 与深度状态均由管理器拥有。
     * 示例：在 `drawSodiumOverlay(...) { mesh.draw() }` 中调用。
     * @throws IllegalStateException 网格已释放、当前无 shader 或不在渲染线程
     */
    fun draw() {
        check(!isClosed) { "地形覆盖网格已释放" }
        withBound {
            val program = glGetInteger(GL_CURRENT_PROGRAM)
            check(program != 0) { "地形覆盖网格绘制时没有活动 shader" }
            if (configuredProgram != program) configureAttributes(program)
            glDrawElements(
                GL_TRIANGLES,
                indexCount,
                if (indexType == IndexType.SHORT) GL_UNSIGNED_SHORT else GL_UNSIGNED_INT,
                0L,
            )
        }
    }

    /** 按 RenderPearl 的显式名称和格式绑定 shader 属性，避免重新链接后属性位置改变。 */
    private fun configureAttributes(program: Int) {
        enabledAttributes.forEach(::glDisableVertexAttribArray)
        enabledAttributes.clear()
        for (element in format.elements) {
            val location = glGetAttribLocation(program, element.name())
            if (location < 0) continue
            val components = element.format().componentCount()
            val offset = element.offset().toLong()
            when (element.format()) {
                GpuFormat.RGB32_FLOAT, GpuFormat.RG32_FLOAT -> {
                    glVertexAttribPointer(location, components, GL_FLOAT, false, format.vertexSize, offset)
                }
                GpuFormat.RGBA8_UNORM -> {
                    glVertexAttribPointer(location, components, GL_UNSIGNED_BYTE, true, format.vertexSize, offset)
                }
                GpuFormat.RGBA8_SNORM -> {
                    glVertexAttribPointer(location, components, GL_BYTE, true, format.vertexSize, offset)
                }
                GpuFormat.RG16_SINT -> {
                    glVertexAttribIPointer(location, components, GL_SHORT, format.vertexSize, offset)
                }
                else -> error("不支持的地形覆盖属性格式：${element.format()}")
            }
            glEnableVertexAttribArray(location)
            enabledAttributes += location
        }
        configuredProgram = program
    }

    /** 把原版无显式索引的四边形拓扑展开成两组三角形，索引宽度沿用原版选择。 */
    private fun sequentialIndices(vertexCount: Int): ByteBuffer {
        val result = MemoryUtil.memAlloc(indexCount * indexType.bytes)
        try {
            for (base in 0 until vertexCount step 4) {
                for (index in intArrayOf(base, base + 1, base + 2, base + 2, base + 3, base)) {
                    if (indexType == IndexType.SHORT) result.putShort(index.toShort()) else result.putInt(index)
                }
            }
            return result.flip()
        } catch (failure: Throwable) {
            MemoryUtil.memFree(result)
            throw failure
        }
    }

    /** 只改本 VAO 的索引与属性绑定，并同时恢复真实 GL 状态和 RenderPearl 缓存。 */
    private inline fun withBound(block: () -> Unit) {
        RenderSystem.assertOnRenderThread()
        val previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val previousIndexBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING)
        GlStateManager._glBindVertexArray(vertexArray)
        glBindVertexArray(vertexArray)
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        try {
            block()
        } finally {
            GlStateManager._glBindVertexArray(previousVertexArray)
            glBindVertexArray(previousVertexArray)
            if (previousVertexArray != 0) {
                GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, previousIndexBuffer)
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, previousIndexBuffer)
            }
            GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
    }

    /**
     * 幂等释放本对象拥有的三个 GL 对象，不删除外部纹理、shader 或 CPU 数据。
     * 示例：区段替换时调用 `mesh.close()`。
     */
    override fun close() {
        RenderSystem.assertOnRenderThread()
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray)
        if (vertexBuffer != 0) glDeleteBuffers(vertexBuffer)
        if (indexBuffer != 0) glDeleteBuffers(indexBuffer)
        vertexArray = 0
        vertexBuffer = 0
        indexBuffer = 0
        configuredProgram = 0
        enabledAttributes.clear()
    }
}
