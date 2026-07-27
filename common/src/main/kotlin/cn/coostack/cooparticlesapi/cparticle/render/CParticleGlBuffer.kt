package cn.coostack.cooparticlesapi.cparticle.render

import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL43
import java.nio.FloatBuffer
import java.nio.ByteOrder

/**
 * GPU 粒子实例缓冲: 一个 VBO 同时充当
 * - instanced attribute 源 (渲染, GL3.1 + core/ARB vertex attrib divisor)
 * - std430 SSBO (GL43 compute 模拟, 同一 buffer 名字绑定到 GL_SHADER_STORAGE_BUFFER)
 *
 * 顶点布局: 无 per-vertex 属性, 四个角由 gl_VertexID 生成;
 * 9 个 vec4 实例属性 (divisor=1), stride = [CParticleStore.BYTE_STRIDE].
 */
class CParticleGlBuffer(val capacity: Int) {
    private companion object {
        const val SPARSE_PATCH_THRESHOLD = 64
        const val VISUAL_FLOAT_COUNT = CParticleStore.STRIDE - CParticleStore.OFF_FLAGS
    }

    var vao = 0
        private set
    var vbo = 0
        private set

    private var scratch: FloatBuffer? = null
    private var smallPatchScratch: FloatBuffer? = null

    val initialized: Boolean get() = vao != 0 && vbo != 0

    fun init() {
        if (initialized) return
        val prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val prevVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, capacity.toLong() * CParticleStore.BYTE_STRIDE, GL_DYNAMIC_DRAW)
        for (loc in 0 until 9) {
            glVertexAttribPointer(loc, 4, GL_FLOAT, false, CParticleStore.BYTE_STRIDE, loc * 16L)
            glEnableVertexAttribArray(loc)
            CParticleCapabilities.setVertexAttribDivisor(loc, 1)
        }
        glBindVertexArray(prevVao)
        glBindBuffer(GL_ARRAY_BUFFER, prevVbo)
    }

    private fun scratchBuffer(): FloatBuffer {
        var s = scratch
        if (s == null) {
            s = BufferUtils.createFloatBuffer(capacity * CParticleStore.STRIDE)
            scratch = s
        }
        return s
    }

    private fun smallPatchBuffer(): FloatBuffer {
        var s = smallPatchScratch
        if (s == null) {
            s = BufferUtils.createFloatBuffer(VISUAL_FLOAT_COUNT)
            smallPatchScratch = s
        }
        return s
    }

    /** 上传 [fromSlot, toSlot] 闭区间槽位数据 */
    fun uploadRange(data: FloatArray, fromSlot: Int, toSlot: Int) {
        if (!initialized || fromSlot > toSlot) return
        val from = fromSlot.coerceAtLeast(0)
        val to = toSlot.coerceAtMost(capacity - 1)
        if (from > to) return
        val floatOffset = from * CParticleStore.STRIDE
        val floatCount = (to - from + 1) * CParticleStore.STRIDE
        val s = scratchBuffer()
        s.clear()
        s.put(data, floatOffset, floatCount)
        s.flip()
        val prev = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferSubData(GL_ARRAY_BUFFER, floatOffset.toLong() * 4L, s)
        glBindBuffer(GL_ARRAY_BUFFER, prev)
    }

    /**
     * 上传一批槽位 (增量 spawn 用). 会先按槽位排序并合并相邻段, 减少 GL 调用.
     * 注意: [slots] 的前 [count] 个元素会被原地排序.
     */
    fun uploadSlots(data: FloatArray, slots: IntArray, count: Int) {
        if (!initialized || count <= 0) return
        java.util.Arrays.sort(slots, 0, count)
        val prev = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val s = scratchBuffer()
        var i = 0
        while (i < count) {
            var j = i
            // 合并连续槽位
            while (j + 1 < count && slots[j + 1] == slots[j] + 1) j++
            val from = slots[i]
            val floatOffset = from * CParticleStore.STRIDE
            val floatCount = (slots[j] - from + 1) * CParticleStore.STRIDE
            s.clear()
            s.put(data, floatOffset, floatCount)
            s.flip()
            glBufferSubData(GL_ARRAY_BUFFER, floatOffset.toLong() * 4L, s)
            i = j + 1
        }
        glBindBuffer(GL_ARRAY_BUFFER, prev)
    }

    /**
     * 只补写 flags 之后的渲染字段。GPU compute 掌管的 pos/prev/velocity/age 不会被覆盖。
     */
    fun patchDynamicVisuals(data: FloatArray, slots: IntArray, count: Int) {
        if (!initialized || count <= 0) return
        val prev = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        if (count <= SPARSE_PATCH_THRESHOLD) {
            val s = smallPatchBuffer()
            for (i in 0 until count) {
                val floatOffset = slots[i] * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS
                s.clear()
                s.put(data, floatOffset, VISUAL_FLOAT_COUNT)
                s.flip()
                glBufferSubData(GL_ARRAY_BUFFER, floatOffset.toLong() * 4L, s)
            }
            glBindBuffer(GL_ARRAY_BUFFER, prev)
            return
        }
        val mapped = glMapBufferRange(
            GL_ARRAY_BUFFER,
            0L,
            capacity.toLong() * CParticleStore.BYTE_STRIDE,
            GL_MAP_WRITE_BIT,
        )
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                val base = slots[i] * CParticleStore.STRIDE
                for (field in CParticleStore.OFF_FLAGS until CParticleStore.STRIDE) {
                    mapped.putFloat((base + field) * 4, data[base + field])
                }
            }
            glUnmapBuffer(GL_ARRAY_BUFFER)
        } else {
            val s = smallPatchBuffer()
            for (i in 0 until count) {
                val floatOffset = slots[i] * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS
                s.clear()
                s.put(data, floatOffset, VISUAL_FLOAT_COUNT)
                s.flip()
                glBufferSubData(GL_ARRAY_BUFFER, floatOffset.toLong() * 4L, s)
            }
        }
        glBindBuffer(GL_ARRAY_BUFFER, prev)
    }

    /** 只补写 alive/light/camera flags，不覆盖 compute 掌管的模拟字段。 */
    fun patchFlags(data: FloatArray, slots: IntArray, count: Int) {
        if (!initialized || count <= 0) return
        val prev = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val s = smallPatchBuffer()
        for (i in 0 until count) {
            val offset = slots[i] * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS
            s.clear()
            s.put(data[offset])
            s.flip()
            glBufferSubData(GL_ARRAY_BUFFER, offset.toLong() * 4L, s)
        }
        glBindBuffer(GL_ARRAY_BUFFER, prev)
    }

    /** compute 模拟: 把本缓冲以 SSBO 身份绑定到 binding 点 */
    fun bindShaderStorage(binding: Int) {
        if (!initialized) return
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, vbo)
    }

    /** instanced 绘制 (TRIANGLE_STRIP x4 顶点), 调用方负责程序/纹理/混合状态 */
    fun draw(instances: Int) {
        if (!initialized || instances <= 0) return
        val prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        glBindVertexArray(vao)
        GL31.glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instances)
        glBindVertexArray(prevVao)
    }

    fun release() {
        if (vbo != 0) {
            glDeleteBuffers(vbo)
            vbo = 0
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao)
            vao = 0
        }
        scratch = null
        smallPatchScratch = null
    }
}
