package cn.coostack.cooparticlesapi.cparticle.storage

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil
import java.util.Arrays
import java.nio.FloatBuffer

/** metadata 的独立 SSBO。不会改变渲染粒子 VBO 的 36-float stride。 */
class CParticleMetadataGlBuffer(capacity: Int) {
    var buffer: Int = 0
        private set

    val initialized: Boolean get() = buffer != 0

    private var scratch: FloatBuffer? = null

    var capacity: Int = capacity
        private set

    private fun scratchBuffer(requiredFloats: Int): FloatBuffer {
        val current = scratch
        if (current != null && current.capacity() >= requiredFloats) return current
        val replacement = MemoryUtil.memAllocFloat(requiredFloats)
        if (current != null) MemoryUtil.memFree(current)
        scratch = replacement
        return replacement
    }

    fun init() {
        if (initialized) return
        buffer = GL15.glGenBuffers()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            capacity.toLong() * CParticleMetadataStore.BYTE_STRIDE,
            GL15.GL_DYNAMIC_DRAW,
        )
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
    }

    /** 扩大 metadata SSBO，并在 GPU 内保留已有槽位内容。 */
    internal fun growTo(newCapacity: Int) {
        require(newCapacity > capacity) {
            "newCapacity must be greater than capacity: $newCapacity <= $capacity"
        }
        if (!initialized) {
            capacity = newCapacity
            return
        }

        val oldCapacity = capacity
        val oldBuffer = buffer
        val previousStorageBuffer = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        val replacementBuffer = GL15.glGenBuffers()
        var committed = false
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, replacementBuffer)
            GL15.glBufferData(
                GL31.GL_COPY_WRITE_BUFFER,
                newCapacity.toLong() * CParticleMetadataStore.BYTE_STRIDE,
                GL15.GL_DYNAMIC_DRAW,
            )
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldBuffer)
            GL31.glCopyBufferSubData(
                GL31.GL_COPY_READ_BUFFER,
                GL31.GL_COPY_WRITE_BUFFER,
                0L,
                0L,
                oldCapacity.toLong() * CParticleMetadataStore.BYTE_STRIDE,
            )
            buffer = replacementBuffer
            capacity = newCapacity
            committed = true
        } finally {
            GL15.glBindBuffer(
                GL43.GL_SHADER_STORAGE_BUFFER,
                if (committed && previousStorageBuffer == oldBuffer) replacementBuffer else previousStorageBuffer,
            )
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0)
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0)
            if (committed) GL15.glDeleteBuffers(oldBuffer) else GL15.glDeleteBuffers(replacementBuffer)
        }
    }

    fun uploadSlots(metadata: CParticleMetadataStore, slots: IntArray, count: Int) {
        if (!initialized || count <= 0) return
        Arrays.sort(slots, 0, count)
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            var index = 0
            while (index < count) {
                var end = index
                while (end + 1 < count && slots[end + 1] == slots[end] + 1) {
                    end++
                }
                val firstSlot = slots[index]
                val slotCount = end - index + 1
                val floatOffset = firstSlot * CParticleMetadataStore.STRIDE
                val floatCount = slotCount * CParticleMetadataStore.STRIDE
                val upload = scratchBuffer(floatCount)
                upload.clear()
                upload.put(metadata.data, floatOffset, floatCount)
                upload.flip()
                GL15.glBufferSubData(
                    GL43.GL_SHADER_STORAGE_BUFFER,
                    firstSlot.toLong() * CParticleMetadataStore.BYTE_STRIDE,
                    upload,
                )
                index = end + 1
            }
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
    }

    /** 上传 [fromSlot, toSlot] 闭区间的 metadata，供缓冲重建或 Command 首次启用时使用。 */
    fun uploadRange(metadata: CParticleMetadataStore, fromSlot: Int, toSlot: Int) {
        if (!initialized || fromSlot > toSlot) return
        val from = fromSlot.coerceAtLeast(0)
        val to = toSlot.coerceAtMost(capacity - 1)
        if (from > to) return
        val floatOffset = from * CParticleMetadataStore.STRIDE
        val floatCount = (to - from + 1) * CParticleMetadataStore.STRIDE
        val upload = scratchBuffer(floatCount)
        upload.clear()
        upload.put(metadata.data, floatOffset, floatCount)
        upload.flip()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferSubData(
                GL43.GL_SHADER_STORAGE_BUFFER,
                from.toLong() * CParticleMetadataStore.BYTE_STRIDE,
                upload,
            )
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
    }

    fun bindShaderStorage(binding: Int) {
        if (initialized) GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, buffer)
    }

    fun release() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer)
            buffer = 0
        }
    }

    fun dispose() {
        release()
        scratch?.let(MemoryUtil::memFree)
        scratch = null
    }
}
