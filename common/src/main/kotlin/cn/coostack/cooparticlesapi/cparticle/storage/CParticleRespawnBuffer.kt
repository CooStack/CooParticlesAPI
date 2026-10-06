package cn.coostack.cooparticlesapi.cparticle.storage

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL43
import java.nio.FloatBuffer

/** 重生通道的可增长缓冲；扩容只在 GPU 内复制，且恢复调用前的缓冲绑定。 */
internal class CParticleRespawnBuffer {
    var id = 0
        private set
    var bytes = 0L
        private set

    fun ensure(required: Long) {
        if (required <= bytes) return
        val previousRead = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER)
        val previousWrite = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER)
        val replacement = GL15.glGenBuffers()
        val old = id
        var committed = false
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, replacement)
            GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, required, GL15.GL_DYNAMIC_DRAW)
            GL43.glClearBufferData(GL31.GL_COPY_WRITE_BUFFER, GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, intArrayOf(0))
            if (old != 0) {
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, old)
                GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L, bytes)
            }
            id = replacement
            bytes = required
            committed = true
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, if (committed && previousRead == old && old != 0) id else previousRead)
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, if (committed && previousWrite == old && old != 0) id else previousWrite)
            GL15.glDeleteBuffers(if (committed) old else replacement)
        }
    }

    fun upload(offset: Long, values: FloatArray) {
        val previous = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER)
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, id)
            GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, offset, values)
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, previous)
        }
    }

    /** 上传可复用的 direct buffer，调用方负责设置 position=0、limit=元素数。 */
    fun upload(offset: Long, values: FloatBuffer) {
        val previous = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER)
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, id)
            GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, offset, values)
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, previous)
        }
    }

    fun clear() {
        val previous = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER)
        try {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, id)
            GL43.glClearBufferData(GL31.GL_COPY_WRITE_BUFFER, GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, intArrayOf(0))
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, previous)
        }
    }

    fun copyTo(target: CParticleRespawnBuffer, size: Long) {
        target.ensure(size)
        val previousRead = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER)
        val previousWrite = GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER)
        try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, id)
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, target.id)
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L, size)
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previousRead)
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, previousWrite)
        }
    }

    fun read(values: IntArray) {
        val previous = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER)
        try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, id)
            GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0L, values)
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previous)
        }
    }

    /** 仅供显式 GPU 到 CPU 转换读取运动快照；纯 GPU 重生不调用此方法。 */
    fun readMotion(slot: Int): FloatArray {
        val previous = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER)
        return try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, id)
            FloatArray(8).also { GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, slot * 32L, it) }
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, previous)
        }
    }

    fun release() {
        if (id != 0) GL15.glDeleteBuffers(id)
        id = 0
        bytes = 0L
    }
}
