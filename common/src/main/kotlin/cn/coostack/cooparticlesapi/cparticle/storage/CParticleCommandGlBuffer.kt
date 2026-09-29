package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil

/** Command 的独立 SSBO，避免把大数组放进 compute uniform 限额。 */
class CParticleCommandGlBuffer(
    private val capacity: Int = 128,
) {
    var buffer: Int = 0
        private set

    private var scratch = MemoryUtil.memAllocFloat(capacity * ForceCommand.STRIDE)
    private val uploadedData = FloatArray(capacity * ForceCommand.STRIDE)
    private var uploadedFloatCount = -1

    val initialized: Boolean
        get() = buffer != 0

    fun init() {
        if (initialized) return
        buffer = GL15.glGenBuffers()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            capacity.toLong() * ForceCommand.STRIDE * 4L,
            GL15.GL_DYNAMIC_DRAW,
        )
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
    }

    fun upload(data: FloatArray, commandCount: Int) {
        if (!initialized || commandCount <= 0) return
        require(commandCount <= capacity) { "commandCount exceeds command buffer capacity: $commandCount" }
        val floatCount = commandCount * ForceCommand.STRIDE
        if (matchesUploadedData(data, floatCount)) return
        scratch.clear()
        scratch.put(data, 0, floatCount)
        scratch.flip()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, scratch)
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
        data.copyInto(uploadedData, endIndex = floatCount)
        uploadedFloatCount = floatCount
    }

    fun bindShaderStorage(binding: Int) {
        if (initialized) GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, buffer)
    }

    fun release() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer)
            buffer = 0
        }
        uploadedFloatCount = -1
    }

    fun dispose() {
        release()
        MemoryUtil.memFree(scratch)
    }

    /** 判断当前打包结果是否已经存在于 GPU 缓冲，避免静态 Command 每 tick 重传。 */
    private fun matchesUploadedData(data: FloatArray, floatCount: Int): Boolean {
        if (uploadedFloatCount != floatCount) return false
        for (index in 0 until floatCount) {
            if (uploadedData[index].toRawBits() != data[index].toRawBits()) return false
        }
        return true
    }
}
