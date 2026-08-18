package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import org.lwjgl.opengl.GL31.glDrawElementsInstanced
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryStack
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
        val indexElementBytes = when (indexType) {
            GL_UNSIGNED_BYTE -> 1L
            GL_UNSIGNED_SHORT -> 2L
            GL_UNSIGNED_INT -> 4L
            else -> error("Unsupported index type: $indexType")
        }
        require(indexByteOffset % indexElementBytes == 0L) {
            "Index byte offset must align to index type"
        }
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
    private var nodeMatrixBuffer = 0
    private var allocatedBytes = 0L
    private var allocatedNodeMatrixBytes = 0L
    private var scratch: FloatBuffer? = null
    private var nodeMatrixScratch: FloatBuffer? = null
    private var expandedEntityVao = 0
    private var expandedEntityVbo = 0
    private var expandedEntityCapacity = 0
    private var expandedEntityVertexCount = 0

    val initialized: Boolean
        get() = instanceBuffer != 0 && nodeMatrixBuffer != 0

    /** 在持有 GL 上下文的渲染线程初始化动态实例缓冲。 */
    fun initialize() {
        if (initialized) return
        instanceBuffer = glGenBuffers()
        nodeMatrixBuffer = glGenBuffers()
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
        return withDrawState {
            var drawCount = 0
            batches.forEach { batch ->
                if (batch.instanceCount <= 0) return@forEach
                val binding = bindingResolver(batch.key)
                bindBatch(batch, binding)
                glDrawElementsInstanced(
                    GL_TRIANGLES,
                    binding.indexCount,
                    binding.indexType,
                    binding.indexByteOffset,
                    batch.instanceCount,
                )
                drawCount++
            }
            drawCount
        }
    }

    /** 把一个 CooFX instanced batch 展开成原版 NEW_ENTITY 顶点，供 Iris entity program 消费。 */
    fun expandForIrisEntity(
        batch: CooFxMeshInstanceBatch,
        binding: CooFxMeshDrawBinding,
    ): Int {
        if (!initialized || batch.instanceCount <= 0) {
            expandedEntityVertexCount = 0
            return 0
        }
        val vertexCountLong = binding.indexCount.toLong() * batch.instanceCount
        require(vertexCountLong <= Int.MAX_VALUE) { "Expanded CooFX entity vertex count exceeds Int range" }
        val vertexCount = vertexCountLong.toInt()
        validateIrisEntityFeedbackLayout()
        ensureExpandedEntityCapacity(vertexCount)

        val previousFeedbackBuffer = glGetInteger(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING)
        val previousFeedbackBase = IntArray(1)
        glGetIntegeri_v(GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0, previousFeedbackBase)
        val rasterizerDiscardEnabled = glIsEnabled(GL_RASTERIZER_DISCARD)
        var feedbackActive = false
        try {
            withDrawState {
                bindBatch(batch, binding)
                glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, expandedEntityVbo)
                glEnable(GL_RASTERIZER_DISCARD)
                glBeginTransformFeedback(GL_TRIANGLES)
                feedbackActive = true
                glDrawElementsInstanced(
                    GL_TRIANGLES,
                    binding.indexCount,
                    binding.indexType,
                    binding.indexByteOffset,
                    batch.instanceCount,
                )
                glEndTransformFeedback()
                feedbackActive = false
            }
            expandedEntityVertexCount = vertexCount
            return vertexCount
        } finally {
            if (feedbackActive) glEndTransformFeedback()
            if (rasterizerDiscardEnabled) glEnable(GL_RASTERIZER_DISCARD) else glDisable(GL_RASTERIZER_DISCARD)
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, previousFeedbackBase[0])
            glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, previousFeedbackBuffer)
        }
    }

    /** 使用当前 Iris entity program 绘制最近一次 GPU 展开的 NEW_ENTITY 顶点。 */
    fun drawExpandedIrisEntity(vertexCount: Int) {
        if (expandedEntityVao == 0 || vertexCount <= 0 || vertexCount != expandedEntityVertexCount) return
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        try {
            glBindVertexArray(expandedEntityVao)
            glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        } finally {
            glBindVertexArray(previousVao)
        }
    }

    /** 在持有 GL 上下文的渲染线程释放实例缓冲和 Direct scratch。 */
    fun release() {
        if (instanceBuffer != 0) {
            glDeleteBuffers(instanceBuffer)
            instanceBuffer = 0
        }
        if (nodeMatrixBuffer != 0) {
            glDeleteBuffers(nodeMatrixBuffer)
            nodeMatrixBuffer = 0
        }
        if (expandedEntityVbo != 0) {
            glDeleteBuffers(expandedEntityVbo)
            expandedEntityVbo = 0
        }
        if (expandedEntityVao != 0) {
            glDeleteVertexArrays(expandedEntityVao)
            expandedEntityVao = 0
        }
        allocatedBytes = 0L
        allocatedNodeMatrixBytes = 0L
        expandedEntityCapacity = 0
        expandedEntityVertexCount = 0
        scratch?.let(MemoryUtil::memFree)
        scratch = null
        nodeMatrixScratch?.let(MemoryUtil::memFree)
        nodeMatrixScratch = null
    }

    private fun bindBatch(batch: CooFxMeshInstanceBatch, binding: CooFxMeshDrawBinding) {
        glBindVertexArray(binding.vertexArrayObject)
        glVertexAttrib3f(1, 0F, 1F, 0F)
        glVertexAttrib2f(2, 0F, 0F)
        glVertexAttrib4f(3, 1F, 1F, 1F, 1F)
        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer)
        ensureCapacity(batch.instanceData.size.toLong() * Float.SIZE_BYTES)
        scratch = upload(batch.instanceData, scratch)
        val instanceAttributes = CooFxMeshInstanceLayout.attributes(binding.firstInstanceAttributeLocation)
        instanceAttributes.forEach { attribute ->
            glVertexAttribPointer(
                attribute.shaderLocation,
                4,
                GL_FLOAT,
                false,
                CooFxMeshInstanceLayout.BYTE_STRIDE,
                attribute.byteOffset.toLong(),
            )
            glEnableVertexAttribArray(attribute.shaderLocation)
            CParticleCapabilities.setVertexAttribDivisor(attribute.shaderLocation, attribute.divisor)
        }
        glBindBuffer(GL_ARRAY_BUFFER, nodeMatrixBuffer)
        ensureNodeMatrixCapacity(batch.nodeMatrixData.size.toLong() * Float.SIZE_BYTES)
        nodeMatrixScratch = upload(batch.nodeMatrixData, nodeMatrixScratch)
        val firstNodeMatrixLocation = binding.firstInstanceAttributeLocation + instanceAttributes.size
        repeat(3) { row ->
            val location = firstNodeMatrixLocation + row
            glVertexAttribPointer(
                location,
                4,
                GL_FLOAT,
                false,
                12 * Float.SIZE_BYTES,
                (row * 4 * Float.SIZE_BYTES).toLong(),
            )
            glEnableVertexAttribArray(location)
            CParticleCapabilities.setVertexAttribDivisor(location, 1)
        }
    }

    private fun <T> withDrawState(block: () -> T): T {
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val attributeCount = glGetInteger(GL_MAX_VERTEX_ATTRIBS)
        val previousEnabled = IntArray(attributeCount)
        val previousDivisors = IntArray(attributeCount)
        for (location in 0 until attributeCount) {
            previousEnabled[location] = glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_ENABLED)
            previousDivisors[location] = glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_DIVISOR)
        }
        return MemoryStack.stackPush().use { stack ->
            val previousNormal = stack.mallocFloat(4)
            val previousTexCoord = stack.mallocFloat(4)
            val previousColor = stack.mallocFloat(4)
            glGetVertexAttribfv(1, GL_CURRENT_VERTEX_ATTRIB, previousNormal)
            glGetVertexAttribfv(2, GL_CURRENT_VERTEX_ATTRIB, previousTexCoord)
            glGetVertexAttribfv(3, GL_CURRENT_VERTEX_ATTRIB, previousColor)
            try {
                block()
            } finally {
                glBindVertexArray(previousVao)
                glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
                for (location in 0 until attributeCount) {
                    if (previousEnabled[location] == GL_TRUE) {
                        glEnableVertexAttribArray(location)
                    } else {
                        glDisableVertexAttribArray(location)
                    }
                    CParticleCapabilities.setVertexAttribDivisor(location, previousDivisors[location])
                }
                glVertexAttrib4fv(1, previousNormal)
                glVertexAttrib4fv(2, previousTexCoord)
                glVertexAttrib4fv(3, previousColor)
            }
        }
    }

    private fun ensureCapacity(requiredBytes: Long) {
        if (requiredBytes <= allocatedBytes) return
        allocatedBytes = maxOf(requiredBytes, (allocatedBytes * 2L).coerceAtLeast(CooFxMeshInstanceLayout.BYTE_STRIDE.toLong()))
        glBufferData(GL_ARRAY_BUFFER, allocatedBytes, GL_DYNAMIC_DRAW)
    }

    private fun ensureNodeMatrixCapacity(requiredBytes: Long) {
        if (requiredBytes <= allocatedNodeMatrixBytes) return
        allocatedNodeMatrixBytes = maxOf(requiredBytes, (allocatedNodeMatrixBytes * 2L).coerceAtLeast(16L * Float.SIZE_BYTES))
        glBufferData(GL_ARRAY_BUFFER, allocatedNodeMatrixBytes, GL_DYNAMIC_DRAW)
    }

    private fun validateIrisEntityFeedbackLayout() {
        val program = glGetInteger(GL_CURRENT_PROGRAM)
        require(program > 0) { "Iris entity 展开必须绑定 CooFX shader program" }
        val varyingCount = glGetProgrami(program, GL_TRANSFORM_FEEDBACK_VARYINGS)
        val expectedNames = arrayOf(
            "tfEntityPosition",
            "tfEntityColor",
            "tfEntityUv",
            "tfEntityOverlay",
            "tfEntityLight",
            "tfEntityNormal",
        )
        require(varyingCount == expectedNames.size) {
            "CooFX Iris feedback varying 数量不匹配：$varyingCount"
        }
        MemoryStack.stackPush().use { stack ->
            val varyingSize = stack.mallocInt(1)
            val varyingType = stack.mallocInt(1)
            var byteStride = 0
            expectedNames.forEachIndexed { index, expectedName ->
                val actualName = glGetTransformFeedbackVarying(program, index, varyingSize, varyingType)
                require(actualName == expectedName) {
                    "CooFX Iris feedback varying[$index] 不匹配：$actualName"
                }
                val componentBytes = when (varyingType[0]) {
                    GL_FLOAT_VEC3 -> 12
                    GL_UNSIGNED_INT -> 4
                    GL_FLOAT_VEC2 -> 8
                    else -> error("CooFX Iris feedback varying 类型不支持：${varyingType[0]}")
                }
                byteStride += componentBytes * varyingSize[0]
            }
            require(byteStride == DefaultVertexFormat.NEW_ENTITY.vertexSize) {
                "CooFX Iris feedback stride=$byteStride 与 NEW_ENTITY=" +
                    "${DefaultVertexFormat.NEW_ENTITY.vertexSize} 不一致"
            }
        }
    }

    private fun ensureExpandedEntityCapacity(requiredVertices: Int) {
        if (expandedEntityVao == 0 || expandedEntityVbo == 0) {
            val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
            val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
            try {
                expandedEntityVao = glGenVertexArrays()
                expandedEntityVbo = glGenBuffers()
                glBindVertexArray(expandedEntityVao)
                glBindBuffer(GL_ARRAY_BUFFER, expandedEntityVbo)
                MemoryStack.stackPush().use { stack ->
                    val previousIntegerValues = stack.mallocInt(12)
                    val previousFloatValues = stack.mallocFloat(12)
                    val irisLocations = intArrayOf(6, 7, 8)
                    irisLocations.forEachIndexed { index, location ->
                        glGetVertexAttribIiv(location, GL_CURRENT_VERTEX_ATTRIB, previousIntegerValues.position(index * 4))
                        glGetVertexAttribfv(location, GL_CURRENT_VERTEX_ATTRIB, previousFloatValues.position(index * 4))
                    }
                    DefaultVertexFormat.NEW_ENTITY.setupBufferState()
                    configureExpandedEntityVao()
                    irisLocations.forEachIndexed { index, location ->
                        glVertexAttribI4iv(location, previousIntegerValues.position(index * 4))
                        glVertexAttrib4fv(location, previousFloatValues.position(index * 4))
                    }
                }
            } finally {
                glBindVertexArray(previousVao)
                glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
            }
        }
        if (requiredVertices <= expandedEntityCapacity) return
        var nextCapacity = expandedEntityCapacity.coerceAtLeast(1)
        while (nextCapacity < requiredVertices) {
            nextCapacity = (nextCapacity.toLong() * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        try {
            glBindBuffer(GL_ARRAY_BUFFER, expandedEntityVbo)
            glBufferData(
                GL_ARRAY_BUFFER,
                nextCapacity.toLong() * DefaultVertexFormat.NEW_ENTITY.vertexSize,
                GL_STREAM_DRAW,
            )
        } finally {
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
        expandedEntityCapacity = nextCapacity
    }

    private fun configureExpandedEntityVao() {
        val stride = DefaultVertexFormat.NEW_ENTITY.vertexSize
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, 12L)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 16L)
        glVertexAttribIPointer(3, 2, GL_SHORT, stride, 24L)
        glVertexAttribIPointer(4, 2, GL_SHORT, stride, 28L)
        glVertexAttribPointer(5, 3, GL_BYTE, true, stride, 32L)
        repeat(6) { location -> glEnableVertexAttribArray(location) }
        repeat(6) { location -> CParticleCapabilities.setVertexAttribDivisor(location, 0) }
        disableExpandedIrisAttributes()
    }

    private fun disableExpandedIrisAttributes() {
        glDisableVertexAttribArray(6)
        glDisableVertexAttribArray(7)
        glDisableVertexAttribArray(8)
        CParticleCapabilities.setVertexAttribDivisor(6, 0)
        CParticleCapabilities.setVertexAttribDivisor(7, 0)
        CParticleCapabilities.setVertexAttribDivisor(8, 0)
        glVertexAttribI3i(6, 0, 0, 0)
        glVertexAttrib2f(7, 0F, 0F)
        glVertexAttrib4f(8, 1F, 0F, 0F, 1F)
    }

    private fun upload(data: FloatArray, currentScratch: FloatBuffer?): FloatBuffer {
        var buffer = currentScratch
        if (buffer == null || buffer.capacity() < data.size) {
            buffer?.let(MemoryUtil::memFree)
            buffer = MemoryUtil.memAllocFloat(data.size)
        }
        buffer.clear()
        buffer.put(data)
        buffer.flip()
        glBufferSubData(GL_ARRAY_BUFFER, 0L, buffer)
        return buffer
    }
}
