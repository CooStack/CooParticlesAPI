package cn.coostack.cooparticlesapi.renderer.shader.buffer

import org.lwjgl.opengl.GL43.*
import java.nio.ByteBuffer

class ShaderBufferObject<T>(
    val layout: ShaderBufferLayout<T>,
    private val shaderStorageSupported: Boolean = true
) {
    var bufferId: Int = 0
        private set
    var allocatedBytes: Int = 0
        private set

    private fun target(): Int {
        return when (layout.effectiveBinding(shaderStorageSupported)) {
            ShaderBufferBinding.UNIFORM_BUFFER -> GL_UNIFORM_BUFFER
            ShaderBufferBinding.SHADER_STORAGE_BUFFER -> GL_SHADER_STORAGE_BUFFER
        }
    }

    fun init() {
        if (bufferId == 0) {
            bufferId = glGenBuffers()
        }
    }

    fun upload(value: T) {
        upload(layout.encode(value))
    }

    fun upload(buffer: ByteBuffer) {
        init()
        bind()
        val size = buffer.remaining()
        glBufferData(target(), buffer, GL_DYNAMIC_DRAW)
        allocatedBytes = size
        bindBase()
        unbind()
    }

    fun bind() {
        init()
        glBindBuffer(target(), bufferId)
    }

    fun bindBase() {
        val binding = requireNotNull(layout.assignedBinding) {
            "Shader buffer layout ${layout.name} must be registered before binding"
        }
        glBindBufferBase(target(), binding, bufferId)
    }

    fun unbind() {
        glBindBuffer(target(), 0)
    }

    fun release() {
        if (bufferId != 0) {
            glDeleteBuffers(bufferId)
            bufferId = 0
            allocatedBytes = 0
        }
    }
}
