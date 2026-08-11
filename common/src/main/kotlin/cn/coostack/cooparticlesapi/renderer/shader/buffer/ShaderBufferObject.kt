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

    /**
     * 初始化或准备 `ShaderBufferObject` 的 `init` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`init()`。
     */
    fun init() {
        if (bufferId == 0) {
            bufferId = glGenBuffers()
        }
    }

    /**
     * 执行 `ShaderBufferObject` 的 `upload` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`upload(value = value)`。
     *
     * @param value 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun upload(value: T) {
        upload(layout.encode(value))
    }

    /**
     * 执行 `ShaderBufferObject` 的 `upload` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`upload(buffer = buffer)`。
     *
     * @param buffer 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
     */
    fun upload(buffer: ByteBuffer) {
        init()
        bind()
        val size = buffer.remaining()
        glBufferData(target(), buffer, GL_DYNAMIC_DRAW)
        allocatedBytes = size
        bindBase()
        unbind()
    }

    /**
     * 把输入对象加入 `ShaderBufferObject` 的 `bind` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bind()`。
     */
    fun bind() {
        init()
        glBindBuffer(target(), bufferId)
    }

    /**
     * 把输入对象加入 `ShaderBufferObject` 的 `bindBase` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bindBase()`。
     */
    fun bindBase() {
        val binding = requireNotNull(layout.assignedBinding) {
            "Shader buffer layout ${layout.name} must be registered before binding"
        }
        glBindBufferBase(target(), binding, bufferId)
    }

    /**
     * 从 `ShaderBufferObject` 的 `unbind` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`unbind()`。
     */
    fun unbind() {
        glBindBuffer(target(), 0)
    }

    /**
     * 释放 `ShaderBufferObject` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release()`。
     */
    fun release() {
        if (bufferId != 0) {
            glDeleteBuffers(bufferId)
            bufferId = 0
            allocatedBytes = 0
        }
    }
}
