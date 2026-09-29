package cn.coostack.cooparticlesapi.gpudata

import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL43
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

/**
 * # GPU 数据图层的 OpenGL 后端
 *
 * 使用**着色器存储缓冲（SSBO）**承载图层数据，理由如下：
 *
 * 1. 项目 GPU 模拟基线是 `#version 430 core` compute（`cparticle_sim.comp` 使用 `std430` SSBO 与
 *    `glDispatchCompute`），OpenGL 4.3 已保证 SSBO 可用，不需要额外能力探测。
 * 2. 图层元素是 float 数值数据，SSBO 不经过采样器、不参与颜色空间转换、不受 mipmap 与过滤影响，
 *    也不占用纹理单元；相比之下 buffer texture 会消耗 sampler 且需要 `texelFetch` 换算下标。
 * 3. 同一缓冲以 `GL_SHADER_STORAGE_BUFFER` 暴露给 compute 与普通 shader 程序，RenderEntity 的
 *    shader 可以直接用 `layout(std430, binding = N) readonly buffer` 读取同一份资源，不需要
 *    compute 私有副本。
 *
 * 缓冲只增不减：容量不足时按 [CooGpuDataLayer.capacity] 重建并整段重传。容量足够时只上传脏区间，
 * 静态图层在首次上传后不再产生任何 GL 调用。
 *
 * 所有方法都只在客户端渲染线程调用，实例不携带任何服务端可见状态。
 */
class CooGpuDataGlBuffer {
    /** GL buffer 名字；0 表示尚未创建。 */
    var buffer: Int = 0
        private set

    /** 已分配的元素容量，用于判断是否需要重建。 */
    var glCapacity: Int = 0
        private set

    private var scratch = MemoryUtil.memAllocFloat(INITIAL_SCRATCH_FLOATS)

    /** 缓冲内容是否已经失效，需要在下一次 [upload] 时整段重传。 */
    private var fullUploadRequired = true

    /** 已经写入 GPU 的内容版本，用于跳过静态图层的重复上传。 */
    private var residentVersion = -1

    val initialized: Boolean
        get() = buffer != 0

    /**
     * 确保 GL buffer 已创建且容量足够容纳图层的分配容量。
     *
     * @param layer 要承载的图层
     * @return 本次是否重建了缓冲；重建后下一次 [upload] 会整段重传
     */
    fun prepare(layer: CooGpuDataLayer): Boolean {
        if (!initialized) {
            buffer = GL15.glGenBuffers()
            allocate(layer)
            return true
        }
        if (layer.capacity <= glCapacity) return false
        allocate(layer)
        return true
    }

    /**
     * 按需上传图层数据。
     *
     * 静态图层（版本未变）不会产生任何 GL 调用。动态图层只上传脏区间；缓冲刚重建时整段上传。
     *
     * @param layer 要上传的图层
     * @return 本次是否执行了上传
     */
    fun upload(layer: CooGpuDataLayer): Boolean {
        if (!initialized) return false
        if (!fullUploadRequired && residentVersion == layer.version) return false
        if (layer.elementCount <= 0) {
            residentVersion = layer.version
            fullUploadRequired = false
            return false
        }
        val from = if (fullUploadRequired) 0 else layer.dirtyFrom
        val to = if (fullUploadRequired) layer.floatCount - 1 else layer.dirtyTo
        if (from < 0 || to < from) {
            residentVersion = layer.version
            fullUploadRequired = false
            return false
        }
        val floatCount = to - from + 1
        val s = scratch(floatCount)
        s.clear()
        s.put(layer.data, from, floatCount)
        s.flip()
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, from.toLong() * Float.SIZE_BYTES, s)
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
        residentVersion = layer.version
        fullUploadRequired = false
        return true
    }

    /**
     * 把图层缓冲绑定到指定的着色器存储绑定点。
     *
     * 示例：compute 路径求值前把路径图层绑定到 [CooGpuDataBindingPoints.PATH]。
     * 禁止：绑定点必须来自 [CooGpuDataBindingPoints]，不能就地写死数字。
     *
     * @param binding 着色器存储缓冲绑定点
     */
    fun bindShaderStorage(binding: Int) {
        if (initialized) GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, buffer)
    }

    /** 释放 GL buffer；CPU 侧图层数据不受影响，下次使用时会重新创建并整段上传。 */
    fun release() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer)
            buffer = 0
        }
        glCapacity = 0
        residentVersion = -1
        fullUploadRequired = true
    }

    /** 释放 GL buffer 与 CPU 侧上传暂存，用于彻底销毁。 */
    fun dispose() {
        release()
        MemoryUtil.memFree(scratch)
        scratch = MemoryUtil.memAllocFloat(0)
    }

    private fun allocate(layer: CooGpuDataLayer) {
        val floatCapacity = layer.allocatedFloatCount
        val previous = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING)
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer)
        try {
            GL15.glBufferData(
                GL43.GL_SHADER_STORAGE_BUFFER,
                floatCapacity.toLong() * Float.SIZE_BYTES,
                GL15.GL_DYNAMIC_DRAW,
            )
        } finally {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previous)
        }
        glCapacity = layer.capacity
        fullUploadRequired = true
        residentVersion = -1
    }

    private fun scratch(requiredFloats: Int): FloatBuffer {
        if (requiredFloats > 0 && scratch.capacity() >= requiredFloats) return scratch
        val next = if (scratch.capacity() <= 0) {
            maxOf(requiredFloats, 1)
        } else {
            maxOf(requiredFloats.toLong(), scratch.capacity().toLong() * 2L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        MemoryUtil.memFree(scratch)
        scratch = MemoryUtil.memAllocFloat(next)
        return scratch
    }

    private companion object {
        /** 初始上传暂存容量；实际需要更大时会立即增长。 */
        const val INITIAL_SCRATCH_FLOATS = 256
    }
}
