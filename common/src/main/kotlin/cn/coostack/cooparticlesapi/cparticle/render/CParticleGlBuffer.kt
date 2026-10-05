package cn.coostack.cooparticlesapi.cparticle.render

import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/**
 * 计算 Direct scratch 的下一次容量。
 * 首次按实际需求分配，后续最多翻倍，并始终受 system 最大容量约束。
 *
 * @param currentCapacity 当前 float 容量，尚未分配时为 0
 * @param requiredFloats 本次上传需要的 float 数量
 * @param maxFloats 当前 system 可上传的最大 float 数量
 * @return 满足本次上传的下一容量
 */
internal fun nextScratchCapacity(currentCapacity: Int, requiredFloats: Int, maxFloats: Int): Int {
    require(maxFloats > 0) { "maxFloats must be positive" }
    require(requiredFloats in 1..maxFloats) {
        "requiredFloats must be in 1..maxFloats"
    }
    if (requiredFloats <= currentCapacity) return currentCapacity
    if (currentCapacity <= 0) return requiredFloats
    return maxOf(requiredFloats.toLong(), currentCapacity.toLong() * 2L)
        .coerceAtMost(maxFloats.toLong())
        .toInt()
}

/**
 * GPU 粒子实例缓冲: 一个 VBO 同时充当
 * - instanced attribute 源 (渲染, GL3.1 + core/ARB vertex attrib divisor)
 * - std430 SSBO (GL43 compute 模拟, 同一 buffer 名字绑定到 GL_SHADER_STORAGE_BUFFER)
 *
 * 顶点布局: 无 per-vertex 属性, 六个三角形顶点由 gl_VertexID 生成;
 * 9 个 vec4 实例属性 (divisor=1), stride = [CParticleStore.BYTE_STRIDE].
 */
class CParticleGlBuffer(capacity: Int) {
    private companion object {
        const val VISUAL_FLOAT_COUNT = CParticleStore.STRIDE - CParticleStore.OFF_FLAGS
        const val EXPANDED_VERTICES_PER_PARTICLE = 6

        /**
         * 认为一次 `glBufferSubData` 大致等价于多少个字节的映射写入。
         *
         * 这是**用于选择策略的量级估计**，不是实测值：一次 GL 状态切换加命令排队的成本远高于
         * 几百字节的内存拷贝，取 4 KiB 参与成本估计，不代表跨驱动的性能保证。
         */
        const val BYTES_PER_GL_CALL = 4096L

        /**
         * 在考虑映射前允许直接补写的槽位数量。
         *
         * 超过本值才考虑整段映射；日常的少量死亡仍然逐槽上传。
         */
        const val PATCH_FLAG_CALL_BUDGET = 64
    }

    var vao = 0
        private set
    var vbo = 0
        private set

    private var expandedVao = 0
    private var expandedVbo = 0
    private var expandedCapacity = 0
    private var expandedInstances = 0
    private var configuredFirstSlot = -1

    private var scratch: FloatBuffer? = null
    private var smallPatchScratch: FloatBuffer? = null

    var capacity: Int = capacity
        private set

    val initialized: Boolean get() = vao != 0 && vbo != 0

    /**
     * 只读映射模拟结果，由 system 在第一颗受关注粒子死亡时调用。
     * 同批死亡共享一次映射，仅访问死亡槽位；调用方必须在任何上传、扩容前解除映射。
     * 保留调用前的 ARRAY_BUFFER 绑定，不干扰 Iris 或其他渲染流程。
     */
    internal fun mapDeathReadback(): ByteBuffer {
        check(initialized) { "Particle buffer is not initialized" }
        val previous = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        try {
            return checkNotNull(glMapBufferRange(
                GL_ARRAY_BUFFER, 0L, capacity.toLong() * CParticleStore.BYTE_STRIDE, GL_MAP_READ_BIT,
            )) { "Unable to map particle death state" }.order(ByteOrder.nativeOrder())
        } finally {
            glBindBuffer(GL_ARRAY_BUFFER, previous)
        }
    }

    /** 解除死亡快照映射并恢复原绑定；读回失败不静默替换为过期的 CPU 出生数据。 */
    internal fun unmapDeathReadback() {
        val previous = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        try {
            check(glUnmapBuffer(GL_ARRAY_BUFFER)) { "Particle death readback became invalid" }
        } finally {
            glBindBuffer(GL_ARRAY_BUFFER, previous)
        }
    }

    fun init() {
        if (initialized) return
        val prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val prevVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, capacity.toLong() * CParticleStore.BYTE_STRIDE, GL_DYNAMIC_DRAW)
        configureInstanceAttributes(0)
        restoreVertexArray(prevVao)
        glBindBuffer(GL_ARRAY_BUFFER, prevVbo)
    }

    /**
     * 扩大实例 VBO，并在 GPU 内复制已有模拟结果。
     *
     * @param newCapacity 新实例容量，必须大于当前容量
     */
    internal fun growTo(newCapacity: Int) {
        require(newCapacity > capacity) {
            "newCapacity must be greater than capacity: $newCapacity <= $capacity"
        }
        if (!initialized) {
            capacity = newCapacity
            return
        }

        val oldCapacity = capacity
        val oldVbo = vbo
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val replacementVbo = glGenBuffers()
        var committed = false
        try {
            glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, replacementVbo)
            glBufferData(
                GL31.GL_COPY_WRITE_BUFFER,
                newCapacity.toLong() * CParticleStore.BYTE_STRIDE,
                GL_DYNAMIC_DRAW,
            )
            glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldVbo)
            GL31.glCopyBufferSubData(
                GL31.GL_COPY_READ_BUFFER,
                GL31.GL_COPY_WRITE_BUFFER,
                0L,
                0L,
                oldCapacity.toLong() * CParticleStore.BYTE_STRIDE,
            )

            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, replacementVbo)
            configureInstanceAttributes(configuredFirstSlot.coerceAtLeast(0))
            vbo = replacementVbo
            capacity = newCapacity
            committed = true
        } finally {
            restoreVertexArray(previousVao)
            glBindBuffer(
                GL_ARRAY_BUFFER,
                if (committed && previousArrayBuffer == oldVbo) replacementVbo else previousArrayBuffer,
            )
            glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0)
            glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0)
            if (committed) glDeleteBuffers(oldVbo) else glDeleteBuffers(replacementVbo)
        }
    }

    private fun configureInstanceAttributes(firstSlot: Int) {
        val firstByteOffset = firstSlot.toLong() * CParticleStore.BYTE_STRIDE
        for (loc in 0 until 9) {
            glVertexAttribPointer(
                loc,
                4,
                GL_FLOAT,
                false,
                CParticleStore.BYTE_STRIDE,
                firstByteOffset + loc * 16L,
            )
            glEnableVertexAttribArray(loc)
            CParticleCapabilities.setVertexAttribDivisor(loc, 1)
        }
        configuredFirstSlot = firstSlot
    }

    /** 当前 VAO 改为从指定实例槽位读取。调用前必须绑定本缓冲的 VAO。 */
    private fun bindInstanceRange(firstSlot: Int) {
        if (configuredFirstSlot == firstSlot) return
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        configureInstanceAttributes(firstSlot)
        glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
    }

    /** 按本次实际上传量获取 scratch；增长时立即释放旧 Direct Buffer。 */
    private fun scratchBuffer(requiredFloats: Int): FloatBuffer {
        val current = scratch
        if (current != null && current.capacity() >= requiredFloats) return current
        val nextCapacity = nextScratchCapacity(
            current?.capacity() ?: 0,
            requiredFloats,
            capacity * CParticleStore.STRIDE,
        )
        val replacement = MemoryUtil.memAllocFloat(nextCapacity)
        if (current != null) MemoryUtil.memFree(current)
        scratch = replacement
        return replacement
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
        val s = scratchBuffer(floatCount)
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
        sortSlotsIfNeeded(slots, count)
        val prev = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        var i = 0
        while (i < count) {
            var j = i
            // 合并连续槽位
            while (j + 1 < count && slots[j + 1] == slots[j] + 1) j++
            val from = slots[i]
            val floatOffset = from * CParticleStore.STRIDE
            val floatCount = (slots[j] - from + 1) * CParticleStore.STRIDE
            val s = scratchBuffer(floatCount)
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
        if (count <= 64) {
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

    /**
     * 大量 flags 补写时，映射整段缓冲并按交错布局写回。
     *
     * 逐槽位 `glBufferSubData` 虽然每次只有 4 字节，但每个调用都要走一遍驱动状态切换；
     * 大量粒子同时死亡或大量生成时，调用次数会直接变成帧耗时。映射一次的成本与缓冲容量成正比、
     * 因此按待补写槽位数估计逐槽调用成本，再决定是否使用映射。
     *
     * @param data 交错粒子数据
     * @param slots 需要补写的槽位；调用方保证已经排序
     * @param count [slots] 中有效的元素数量
     */
    private fun patchFlagsByMapping(data: FloatArray, slots: IntArray, count: Int) {
        val mapped = glMapBufferRange(
            GL_ARRAY_BUFFER,
            0L,
            capacity.toLong() * CParticleStore.BYTE_STRIDE,
            GL_MAP_WRITE_BIT,
        )
        if (mapped == null) {
            patchFlagsIndividually(data, slots, count)
            return
        }
        try {
            mapped.order(ByteOrder.nativeOrder())
            for (index in 0 until count) {
                val offset = slots[index] * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS
                mapped.putFloat(offset * Float.SIZE_BYTES, data[offset])
            }
        } finally {
            glUnmapBuffer(GL_ARRAY_BUFFER)
        }
    }

    /**
     * 判断一批 flags 补写应该走逐槽上传还是整段映射。
     *
     * flags 并非连续数组，即使槽位相邻也需要独立调用。使用容量和调用次数进行启发式成本比较。
     *
     * @param count 需要补写的槽位数
     */
    private fun shouldMapFlags(count: Int): Boolean {
        if (count <= PATCH_FLAG_CALL_BUDGET) return false
        val mapBytes = capacity.toLong() * CParticleStore.BYTE_STRIDE
        val callSavings = (count - PATCH_FLAG_CALL_BUDGET).toLong() * BYTES_PER_GL_CALL
        return callSavings > mapBytes
    }

    /** 只补写 alive/light/camera flags，不覆盖 compute 掌管的模拟字段。 */
    fun patchFlags(data: FloatArray, slots: IntArray, count: Int) {
        if (!initialized || count <= 0) return
        sortSlotsIfNeeded(slots, count)
        val previous = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        try {
            if (shouldMapFlags(count)) {
                patchFlagsByMapping(data, slots, count)
            } else {
                // flags 按 STRIDE 间隔排列，连续上传标记列表会覆盖运动字段。
                patchFlagsIndividually(data, slots, count)
            }
        } finally {
            glBindBuffer(GL_ARRAY_BUFFER, previous)
        }
    }

    /** [patchFlagsByMapping] 在映射失败时的保守回退：逐个槽位补写。 */
    private fun patchFlagsIndividually(data: FloatArray, slots: IntArray, count: Int) {
        val s = smallPatchBuffer()
        for (index in 0 until count) {
            val offset = slots[index] * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS
            s.clear()
            s.put(data[offset])
            s.flip()
            glBufferSubData(GL_ARRAY_BUFFER, offset.toLong() * Float.SIZE_BYTES, s)
        }
    }

    /**
     * 新生/死亡槽位通常由连续的 freeStack 或同一到期批次产生，天然已经递增。
     * 先做一次线性有序检查，避免百万粒子上传前无条件进入 O(n log n) 排序；
     * 只有槽位被手动 kill 或交错复用后真正乱序时才调用 Arrays.sort。
     */
    private fun sortSlotsIfNeeded(slots: IntArray, count: Int) {
        var previous = slots[0]
        for (index in 1 until count) {
            val current = slots[index]
            if (current < previous) {
                Arrays.sort(slots, 0, count)
                return
            }
            previous = current
        }
    }

    /** compute 模拟: 把本缓冲以 SSBO 身份绑定到 binding 点 */
    fun bindShaderStorage(binding: Int) {
        if (!initialized) return
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, vbo)
    }

    /** instanced 绘制 (TRIANGLES x6 顶点), 调用方负责程序/纹理/混合状态 */
    fun draw(instances: Int) {
        draw(0, instances)
    }

    /** 从 [firstSlot] 开始绘制连续实例区间。 */
    fun draw(firstSlot: Int, instances: Int) {
        if (!initialized || instances <= 0) return
        require(firstSlot >= 0 && instances <= capacity - firstSlot) {
            "invalid instance range: first=$firstSlot count=$instances capacity=$capacity"
        }
        val prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        glBindVertexArray(vao)
        bindInstanceRange(firstSlot)
        GL31.glDrawArraysInstanced(GL_TRIANGLES, 0, EXPANDED_VERTICES_PER_PARTICLE, instances)
        restoreVertexArray(prevVao)
    }

    /**
     * 在 GPU 上把实例展开为原版 `DefaultVertexFormat.PARTICLE` 顶点。
     *
     * 示例：Iris 粒子 program 绘制前调用 `expandForParticleShader(firstAliveSlot, activeSlotCount)`。
     * 禁止在未绑定带 transform-feedback 输出的 CParticle program 时调用。
     *
     * @param instances 需要展开的实例槽位数量
     */
    fun expandForParticleShader(instances: Int) {
        expandForParticleShader(0, instances)
    }

    /** 从 [firstSlot] 开始把连续实例区间展开到紧凑的原版粒子顶点缓冲。 */
    fun expandForParticleShader(firstSlot: Int, instances: Int) {
        if (!initialized || instances <= 0) {
            expandedInstances = 0
            return
        }
        require(firstSlot >= 0 && instances <= capacity - firstSlot) {
            "invalid expansion range: first=$firstSlot count=$instances capacity=$capacity"
        }
        ensureExpandedCapacity(instances)

        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousFeedbackBuffer = glGetInteger(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING)
        val previousFeedbackBase = IntArray(1)
        glGetIntegeri_v(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0, previousFeedbackBase)
        val rasterizerDiscardEnabled = glIsEnabled(GL_RASTERIZER_DISCARD)
        var feedbackActive = false
        try {
            glBindVertexArray(vao)
            bindInstanceRange(firstSlot)
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, expandedVbo)
            glEnable(GL_RASTERIZER_DISCARD)
            glBeginTransformFeedback(GL_TRIANGLES)
            feedbackActive = true
            GL31.glDrawArraysInstanced(
                GL_TRIANGLES,
                0,
                EXPANDED_VERTICES_PER_PARTICLE,
                instances,
            )
            glEndTransformFeedback()
            feedbackActive = false
            expandedInstances = instances
        } finally {
            if (feedbackActive) glEndTransformFeedback()
            if (rasterizerDiscardEnabled) glEnable(GL_RASTERIZER_DISCARD) else glDisable(GL_RASTERIZER_DISCARD)
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, previousFeedbackBase[0])
            glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, previousFeedbackBuffer)
            restoreVertexArray(previousVao)
        }
    }

    /**
     * 使用当前 Iris/vanilla 粒子 program 绘制最近一次 GPU 展开的顶点。
     *
     * 示例：`expandForParticleShader(count)` 后调用 `drawExpanded(count)`。
     * 禁止传入大于最近展开数量的值，未初始化部分没有有效顶点。
     *
     * @param instances 要绘制的已展开实例数量
     */
    fun drawExpanded(instances: Int) {
        if (expandedVao == 0 || instances <= 0 || instances > expandedInstances) return
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        glBindVertexArray(expandedVao)
        glDrawArrays(GL_TRIANGLES, 0, instances * EXPANDED_VERTICES_PER_PARTICLE)
        restoreVertexArray(previousVao)
    }

    /**
     * 按实例高水位增长 Iris 展开缓冲，并初始化原版粒子 VAO。
     *
     * 示例：首次展开 300 个实例时分配能容纳至少 300 个实例的缓冲。
     * 禁止按系统最大容量预分配，空闲系统不应长期占用展开缓冲。
     *
     * @param requiredInstances 本次展开所需的实例槽位数
     */
    private fun ensureExpandedCapacity(requiredInstances: Int) {
        if (expandedVao == 0 || expandedVbo == 0) {
            val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
            val previousVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
            expandedVao = glGenVertexArrays()
            expandedVbo = glGenBuffers()
            glBindVertexArray(expandedVao)
            glBindBuffer(GL_ARRAY_BUFFER, expandedVbo)
            DefaultVertexFormat.PARTICLE.setupBufferState()
            restoreVertexArray(previousVao)
            glBindBuffer(GL_ARRAY_BUFFER, previousVbo)
        }
        if (requiredInstances <= expandedCapacity) return

        var nextCapacity = expandedCapacity.coerceAtLeast(1)
        while (nextCapacity < requiredInstances) {
            nextCapacity = (nextCapacity * 2).coerceAtMost(capacity)
            if (nextCapacity == capacity) break
        }
        val previousVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        glBindBuffer(GL_ARRAY_BUFFER, expandedVbo)
        glBufferData(
            GL_ARRAY_BUFFER,
            nextCapacity.toLong() * EXPANDED_VERTICES_PER_PARTICLE * DefaultVertexFormat.PARTICLE.vertexSize.toLong(),
            GL_STREAM_DRAW,
        )
        glBindBuffer(GL_ARRAY_BUFFER, previousVbo)
        expandedCapacity = nextCapacity
    }

    private fun restoreVertexArray(vertexArrayObject: Int) {
        glBindVertexArray(if (vertexArrayObject != 0 && glIsVertexArray(vertexArrayObject)) vertexArrayObject else 0)
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
        if (expandedVbo != 0) {
            glDeleteBuffers(expandedVbo)
            expandedVbo = 0
        }
        if (expandedVao != 0) {
            glDeleteVertexArrays(expandedVao)
            expandedVao = 0
        }
        expandedCapacity = 0
        expandedInstances = 0
        configuredFirstSlot = -1
        scratch?.let { MemoryUtil.memFree(it) }
        scratch = null
        smallPatchScratch = null
    }
}
