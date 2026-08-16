package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.GL_FLOAT
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL31.glDrawElementsInstanced
import org.lwjgl.opengl.GL33.GL_TRIANGLES
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

/** 外部 GPU package 为一个 batch key 解析出的静态 mesh 绘制绑定。 */
data class CooFxMeshDrawBinding(
    val vertexArrayObject: Int,
    val indexCount: Int,
    val indexType: Int,
    val indexByteOffset: Long,
    val firstInstanceAttributeLocation: Int,
) {
    init {
        require(vertexArrayObject > 0) { "Vertex array object must be initialized" }
        require(indexCount > 0) { "Index count must be positive" }
        require(indexByteOffset >= 0L) { "Index byte offset must be non-negative" }
        require(firstInstanceAttributeLocation >= 0) { "Instance attribute location must be non-negative" }
    }
}

/**
 * GL3.3 最低路径的网格粒子实例绘制器。
 *
 * 本类型只拥有动态实例 VBO。静态 VAO/EBO、program、纹理和光栅状态由 compiled GPU package
 * 与 Coo Pipeline 作用域管理，调用方通过 [bindingResolver] 提供绑定。
 */
class CooFxMeshParticleRenderer {
    private var instanceBuffer = 0
    private var allocatedBytes = 0L
    private var scratch: FloatBuffer? = null

    val initialized: Boolean
        get() = instanceBuffer != 0

    /** 在持有 GL 上下文的渲染线程初始化动态实例缓冲。 */
    fun initialize() {
        if (initialized) return
        instanceBuffer = glGenBuffers()
    }

    /**
     * 上传并绘制每个稳定批次；每个非空批次恰好执行一次 instanced draw。
     * program、texture 和 CooGLSLStateManager 状态必须由调用方在外层绑定。
     */
    fun render(
        batches: List<CooFxMeshInstanceBatch>,
        bindingResolver: (CooFxMeshBatchKey) -> CooFxMeshDrawBinding,
    ): Int {
        if (!initialized) return 0
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        var drawCount = 0
        try {
            batches.forEach { batch ->
                if (batch.instanceCount <= 0) return@forEach
                val binding = bindingResolver(batch.key)
                glBindVertexArray(binding.vertexArrayObject)
                glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer)
                ensureCapacity(batch.instanceData.size.toLong() * Float.SIZE_BYTES)
                upload(batch.instanceData)
                CooFxMeshInstanceLayout.attributes(binding.firstInstanceAttributeLocation).forEach { attribute ->
                    glVertexAttribPointer(
                        attribute.location,
                        attribute.componentCount,
                        GL_FLOAT,
                        false,
                        attribute.byteStride,
                        attribute.byteOffset.toLong(),
                    )
                    glEnableVertexAttribArray(attribute.location)
                    CParticleCapabilities.setVertexAttribDivisor(attribute.location, attribute.divisor)
                }
                glDrawElementsInstanced(
                    GL_TRIANGLES,
                    binding.indexCount,
                    binding.indexType,
                    binding.indexByteOffset,
                    batch.instanceCount,
                )
                drawCount++
            }
        } finally {
            glBindVertexArray(previousVao)
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
        return drawCount
    }

    /** 在持有 GL 上下文的渲染线程释放实例缓冲和 Direct scratch。 */
    fun release() {
        if (instanceBuffer != 0) {
            glDeleteBuffers(instanceBuffer)
            instanceBuffer = 0
        }
        allocatedBytes = 0L
        scratch?.let(MemoryUtil::memFree)
        scratch = null
    }

    private fun ensureCapacity(requiredBytes: Long) {
        if (requiredBytes <= allocatedBytes) return
        allocatedBytes = maxOf(requiredBytes, (allocatedBytes * 2L).coerceAtLeast(CooFxMeshInstanceLayout.BYTE_STRIDE.toLong()))
        glBufferData(GL_ARRAY_BUFFER, allocatedBytes, GL_DYNAMIC_DRAW)
    }

    private fun upload(data: FloatArray) {
        var buffer = scratch
        if (buffer == null || buffer.capacity() < data.size) {
            buffer?.let(MemoryUtil::memFree)
            buffer = MemoryUtil.memAllocFloat(data.size)
            scratch = buffer
        }
        buffer.clear()
        buffer.put(data)
        buffer.flip()
        glBufferSubData(GL_ARRAY_BUFFER, 0L, buffer)
    }
}
