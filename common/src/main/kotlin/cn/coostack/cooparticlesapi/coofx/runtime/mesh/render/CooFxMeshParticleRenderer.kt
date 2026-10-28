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
    private var vertexAttributeCount = 0
    private var validatedFeedbackProgram = 0
    private var frameUploadPrepared = false
    private val drawStateScope = CooFxRenderStateScope(::captureDrawState, ::restoreDrawState)
    private val frameUploadAllocator = CooFxFrameUploadAllocator()
    /** 当前同步批次已经上传的数据，只允许在下一次批次绑定前重绘。 */
    private var preparedBatch: CooFxMeshInstanceBatch? = null
    /** 当前已上传批次所属的静态 mesh 绑定。 */
    private var preparedBinding: CooFxMeshDrawBinding? = null
    /** 当前实例和节点矩阵的流式区间，不为额外 pass 重复预留容量。 */
    private var preparedUploadRange: CooFxFrameUploadRange? = null

    val initialized: Boolean
        get() = instanceBuffer != 0 && nodeMatrixBuffer != 0

    /** 在持有 GL 上下文的渲染线程初始化动态实例缓冲。 */
    fun initialize() {
        if (initialized) return
        instanceBuffer = glGenBuffers()
        nodeMatrixBuffer = glGenBuffers()
        vertexAttributeCount = glGetInteger(GL_MAX_VERTEX_ATTRIBS)
    }

    /** 在外层批次循环中一次保存并恢复调用方 VAO 状态。 */
    fun <T> withPreservedDrawState(block: () -> T): T = drawStateScope.use(block)

    /** 在当前 world pass 为动态实例数据建立不重叠的流式上传区间。 */
    internal fun prepareFrame(batches: List<CooFxMeshInstanceBatch>) {
        if (!initialized) return
        val instanceBytes = batches.sumOf { it.instanceData.size.toLong() * Float.SIZE_BYTES }
        val nodeMatrixBytes = batches.sumOf { it.nodeMatrixData.size.toLong() * Float.SIZE_BYTES }
        frameUploadAllocator.prepare(instanceBytes, nodeMatrixBytes)
        frameUploadPrepared = true
        if (instanceBytes > 0L) {
            glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer)
            orphanInstanceBuffer(instanceBytes)
        }
        if (nodeMatrixBytes > 0L) {
            glBindBuffer(GL_ARRAY_BUFFER, nodeMatrixBuffer)
            orphanNodeMatrixBuffer(nodeMatrixBytes)
        }
    }

    /** 结束当前 world pass 的流式上传作用域。 */
    internal fun finishFrame() {
        frameUploadPrepared = false
        frameUploadAllocator.clear()
        preparedBatch = null
        preparedBinding = null
        preparedUploadRange = null
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

    /** 把一个 CooFX instanced batch 展开成原版 ENTITY 顶点，供 Iris entity program 消费。 */
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

    /** 使用当前 Iris entity program 的实际 attribute location 绘制最近一次 GPU 展开的 ENTITY 顶点。 */
    fun drawExpandedIrisEntity(vertexCount: Int) {
        if (expandedEntityVao == 0 || vertexCount <= 0 || vertexCount != expandedEntityVertexCount) return
        val previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        try {
            glBindVertexArray(expandedEntityVao)
            glBindBuffer(GL_ARRAY_BUFFER, expandedEntityVbo)
            configureExpandedEntityVao()
            glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        } finally {
            restoreVertexArray(previousVao)
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
    }

    /**
     * 在同一同步批次内复用最近一次上传的实例数据，供 Iris 回退与自发光额外 pass 使用。
     *
     * @param batch 必须为最近一次成功展开或绘制的同一个批次对象
     * @param binding 必须与该批次上传时的静态 mesh 绑定相同
     * @throws IllegalStateException 批次已被替换、结束或从未上传
     */
    internal fun drawPreparedBatch(batch: CooFxMeshInstanceBatch, binding: CooFxMeshDrawBinding) {
        check(preparedBatch === batch && preparedBinding == binding) { "CooFX prepared batch is no longer current" }
        val range = checkNotNull(preparedUploadRange)
        withDrawState {
            configureBatchBinding(binding, range)
            glDrawElementsInstanced(
                GL_TRIANGLES, binding.indexCount, binding.indexType, binding.indexByteOffset, batch.instanceCount,
            )
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
        vertexAttributeCount = 0
        validatedFeedbackProgram = 0
        finishFrame()
        scratch?.let(MemoryUtil::memFree)
        scratch = null
        nodeMatrixScratch?.let(MemoryUtil::memFree)
        nodeMatrixScratch = null
    }

    private fun bindBatch(batch: CooFxMeshInstanceBatch, binding: CooFxMeshDrawBinding) {
        check(glIsVertexArray(binding.vertexArrayObject)) {
            "CooFX mesh draw binding vertex array object is no longer valid: ${binding.vertexArrayObject}"
        }
        val instanceBytes = batch.instanceData.size.toLong() * Float.SIZE_BYTES
        val nodeMatrixBytes = batch.nodeMatrixData.size.toLong() * Float.SIZE_BYTES
        val useFrameUpload = frameUploadPrepared
        val uploadRange = if (useFrameUpload) {
            frameUploadAllocator.reserve(instanceBytes, nodeMatrixBytes)
        } else {
            CooFxFrameUploadRange(0L, 0L)
        }
        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer)
        if (!useFrameUpload) ensureCapacity(instanceBytes)
        scratch = upload(batch.instanceData, scratch, uploadRange.instanceByteOffset)
        glBindBuffer(GL_ARRAY_BUFFER, nodeMatrixBuffer)
        if (!useFrameUpload) ensureNodeMatrixCapacity(nodeMatrixBytes)
        nodeMatrixScratch = upload(batch.nodeMatrixData, nodeMatrixScratch, uploadRange.nodeMatrixByteOffset)
        configureBatchBinding(binding, uploadRange)
        preparedBatch = batch
        preparedBinding = binding
        preparedUploadRange = uploadRange
    }

    /** 只恢复输入布局与已上传区间，不上传数据或推进帧分配器。 */
    private fun configureBatchBinding(binding: CooFxMeshDrawBinding, uploadRange: CooFxFrameUploadRange) {
        glBindVertexArray(binding.vertexArrayObject)
        glVertexAttrib3f(1, 0F, 1F, 0F)
        glVertexAttrib2f(2, 0F, 0F)
        glVertexAttrib4f(3, 1F, 1F, 1F, 1F)
        glBindBuffer(GL_ARRAY_BUFFER, instanceBuffer)
        val instanceAttributes = CooFxMeshInstanceLayout.attributes(binding.firstInstanceAttributeLocation)
        instanceAttributes.forEach { attribute ->
            glVertexAttribPointer(
                attribute.shaderLocation,
                4,
                GL_FLOAT,
                false,
                CooFxMeshInstanceLayout.BYTE_STRIDE,
                uploadRange.instanceByteOffset + attribute.byteOffset,
            )
            glEnableVertexAttribArray(attribute.shaderLocation)
            CParticleCapabilities.setVertexAttribDivisor(attribute.shaderLocation, attribute.divisor)
        }
        glBindBuffer(GL_ARRAY_BUFFER, nodeMatrixBuffer)
        val firstNodeMatrixLocation = binding.firstInstanceAttributeLocation + instanceAttributes.size
        repeat(3) { row ->
            val location = firstNodeMatrixLocation + row
            glVertexAttribPointer(
                location,
                4,
                GL_FLOAT,
                false,
                12 * Float.SIZE_BYTES,
                uploadRange.nodeMatrixByteOffset + row * 4L * Float.SIZE_BYTES,
            )
            glEnableVertexAttribArray(location)
            CParticleCapabilities.setVertexAttribDivisor(location, 1)
        }
    }

    private fun <T> withDrawState(block: () -> T): T = drawStateScope.use(block)

    private fun captureDrawState(): CooFxDrawState {
        val vertexArrayObject = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val capturesVertexArrayState = vertexArrayObject != 0 && glIsVertexArray(vertexArrayObject)
        return MemoryStack.stackPush().use { stack ->
            val normal = readCurrentVertexAttribute(1, stack)
            val textureCoordinates = readCurrentVertexAttribute(2, stack)
            val color = readCurrentVertexAttribute(3, stack)
            if (!capturesVertexArrayState) {
                return@use CooFxDrawState(
                    vertexArrayObject = vertexArrayObject,
                    arrayBufferObject = glGetInteger(GL_ARRAY_BUFFER_BINDING),
                    enabledAttributes = null,
                    attributeDivisors = null,
                    normal = normal,
                    textureCoordinates = textureCoordinates,
                    color = color,
                )
            }
            val attributeCount = vertexAttributeCount.takeIf { it > 0 } ?: glGetInteger(GL_MAX_VERTEX_ATTRIBS)
            val previousEnabled = IntArray(attributeCount)
            val previousDivisors = IntArray(attributeCount)
            for (location in 0 until attributeCount) {
                previousEnabled[location] = glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_ENABLED)
                previousDivisors[location] = glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_DIVISOR)
            }
            CooFxDrawState(
                vertexArrayObject = vertexArrayObject,
                arrayBufferObject = glGetInteger(GL_ARRAY_BUFFER_BINDING),
                enabledAttributes = previousEnabled,
                attributeDivisors = previousDivisors,
                normal = normal,
                textureCoordinates = textureCoordinates,
                color = color,
            )
        }
    }

    private fun readCurrentVertexAttribute(location: Int, stack: MemoryStack): FloatArray {
        val values = stack.mallocFloat(4)
        glGetVertexAttribfv(location, GL_CURRENT_VERTEX_ATTRIB, values)
        return FloatArray(4) { values[it] }
    }

    private fun restoreDrawState(state: CooFxDrawState) {
        val restoresVertexArrayState = restoreVertexArray(state.vertexArrayObject)
        glBindBuffer(GL_ARRAY_BUFFER, state.arrayBufferObject)
        val enabledAttributes = state.enabledAttributes
        val attributeDivisors = state.attributeDivisors
        if (restoresVertexArrayState && enabledAttributes != null && attributeDivisors != null) {
            enabledAttributes.indices.forEach { location ->
                if (enabledAttributes[location] == GL_TRUE) {
                    glEnableVertexAttribArray(location)
                } else {
                    glDisableVertexAttribArray(location)
                }
                CParticleCapabilities.setVertexAttribDivisor(location, attributeDivisors[location])
            }
        }
        state.normal?.let { normal -> glVertexAttrib4f(1, normal[0], normal[1], normal[2], normal[3]) }
        state.textureCoordinates?.let { textureCoordinates ->
            glVertexAttrib4f(
                2,
                textureCoordinates[0],
                textureCoordinates[1],
                textureCoordinates[2],
                textureCoordinates[3],
            )
        }
        state.color?.let { color -> glVertexAttrib4f(3, color[0], color[1], color[2], color[3]) }
    }

    private fun restoreVertexArray(vertexArrayObject: Int): Boolean {
        val restoresVertexArrayState = vertexArrayObject != 0 && glIsVertexArray(vertexArrayObject)
        glBindVertexArray(if (restoresVertexArrayState) vertexArrayObject else 0)
        return restoresVertexArrayState
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

    private fun orphanInstanceBuffer(requiredBytes: Long) {
        if (requiredBytes > allocatedBytes) {
            allocatedBytes = maxOf(
                requiredBytes,
                (allocatedBytes * 2L).coerceAtLeast(CooFxMeshInstanceLayout.BYTE_STRIDE.toLong()),
            )
        }
        glBufferData(GL_ARRAY_BUFFER, allocatedBytes, GL_STREAM_DRAW)
    }

    private fun orphanNodeMatrixBuffer(requiredBytes: Long) {
        if (requiredBytes > allocatedNodeMatrixBytes) {
            allocatedNodeMatrixBytes = maxOf(
                requiredBytes,
                (allocatedNodeMatrixBytes * 2L).coerceAtLeast(16L * Float.SIZE_BYTES),
            )
        }
        glBufferData(GL_ARRAY_BUFFER, allocatedNodeMatrixBytes, GL_STREAM_DRAW)
    }

    private fun validateIrisEntityFeedbackLayout() {
        val program = glGetInteger(GL_CURRENT_PROGRAM)
        require(program > 0) { "Iris entity 展开必须绑定 CooFX shader program" }
        if (program == validatedFeedbackProgram) return
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
            require(byteStride == DefaultVertexFormat.ENTITY.vertexSize) {
                "CooFX Iris feedback stride=$byteStride 与 ENTITY=" +
                    "${DefaultVertexFormat.ENTITY.vertexSize} 不一致"
            }
        }
        validatedFeedbackProgram = program
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
            } finally {
                restoreVertexArray(previousVao)
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
                nextCapacity.toLong() * DefaultVertexFormat.ENTITY.vertexSize,
                GL_STREAM_DRAW,
            )
        } finally {
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
        expandedEntityCapacity = nextCapacity
    }

    /**
     * 按目标 program 的已链接属性位置设置 26.3 ENTITY 布局。
     *
     * Iris 的 VanillaTransformer 将语义名改为 iris_ 前缀；FallbackShader 保留原版语义名。
     * 不启用未提供数据的扩展属性，也不修改调用方的全局 generic attribute 常量。
     */
    private fun configureExpandedEntityVao() {
        val program = glGetInteger(GL_CURRENT_PROGRAM)
        check(program > 0) { "Expanded CooFX entity draw requires an active Iris program" }
        val stride = DefaultVertexFormat.ENTITY.vertexSize
        repeat(vertexAttributeCount) { location ->
            glDisableVertexAttribArray(location)
            CParticleCapabilities.setVertexAttribDivisor(location, 0)
        }
        fun location(semantic: String): Int {
            val irisLocation = glGetAttribLocation(program, "iris_$semantic")
            return if (irisLocation >= 0) irisLocation else glGetAttribLocation(program, semantic)
        }
        fun floatingAttribute(semantic: String, size: Int, type: Int, normalized: Boolean, offset: Long) {
            val location = location(semantic)
            if (location < 0) return
            glVertexAttribPointer(location, size, type, normalized, stride, offset)
            glEnableVertexAttribArray(location)
        }
        fun integerAttribute(semantic: String, offset: Long) {
            val location = location(semantic)
            if (location < 0) return
            glVertexAttribIPointer(location, 2, GL_SHORT, stride, offset)
            glEnableVertexAttribArray(location)
        }
        check(location("Position") >= 0) { "Iris entity program does not expose a supported position attribute" }
        floatingAttribute("Position", 3, GL_FLOAT, false, 0L)
        floatingAttribute("Color", 4, GL_UNSIGNED_BYTE, true, 12L)
        floatingAttribute("UV0", 2, GL_FLOAT, false, 16L)
        integerAttribute("UV1", 24L)
        integerAttribute("UV2", 28L)
        floatingAttribute("Normal", 3, GL_BYTE, true, 32L)
    }

    private fun upload(data: FloatArray, currentScratch: FloatBuffer?, byteOffset: Long): FloatBuffer {
        var buffer = currentScratch
        if (buffer == null || buffer.capacity() < data.size) {
            buffer?.let(MemoryUtil::memFree)
            buffer = MemoryUtil.memAllocFloat(data.size)
        }
        buffer.clear()
        buffer.put(data)
        buffer.flip()
        glBufferSubData(GL_ARRAY_BUFFER, byteOffset, buffer)
        return buffer
    }
}

private data class CooFxDrawState(
    val vertexArrayObject: Int,
    val arrayBufferObject: Int,
    val enabledAttributes: IntArray?,
    val attributeDivisors: IntArray?,
    val normal: FloatArray?,
    val textureCoordinates: FloatArray?,
    val color: FloatArray?,
)

internal data class CooFxFrameUploadRange(
    val instanceByteOffset: Long,
    val nodeMatrixByteOffset: Long,
)

/** 为单个 CooFX world pass 分配不重叠的动态 VBO 写入区间。 */
internal class CooFxFrameUploadAllocator {
    private var instanceLimit = 0L
    private var nodeMatrixLimit = 0L
    private var nextInstanceByteOffset = 0L
    private var nextNodeMatrixByteOffset = 0L
    private var prepared = false

    fun prepare(instanceBytes: Long, nodeMatrixBytes: Long) {
        require(instanceBytes >= 0L) { "Instance upload bytes must not be negative" }
        require(nodeMatrixBytes >= 0L) { "Node matrix upload bytes must not be negative" }
        instanceLimit = instanceBytes
        nodeMatrixLimit = nodeMatrixBytes
        nextInstanceByteOffset = 0L
        nextNodeMatrixByteOffset = 0L
        prepared = true
    }

    fun reserve(instanceBytes: Long, nodeMatrixBytes: Long): CooFxFrameUploadRange {
        check(prepared) { "Frame upload allocator must be prepared before reserving data" }
        require(instanceBytes >= 0L) { "Instance upload bytes must not be negative" }
        require(nodeMatrixBytes >= 0L) { "Node matrix upload bytes must not be negative" }
        check(instanceBytes <= instanceLimit - nextInstanceByteOffset) {
            "Instance upload range exceeds prepared frame capacity"
        }
        check(nodeMatrixBytes <= nodeMatrixLimit - nextNodeMatrixByteOffset) {
            "Node matrix upload range exceeds prepared frame capacity"
        }
        val range = CooFxFrameUploadRange(nextInstanceByteOffset, nextNodeMatrixByteOffset)
        nextInstanceByteOffset += instanceBytes
        nextNodeMatrixByteOffset += nodeMatrixBytes
        return range
    }

    fun clear() {
        instanceLimit = 0L
        nodeMatrixLimit = 0L
        nextInstanceByteOffset = 0L
        nextNodeMatrixByteOffset = 0L
        prepared = false
    }
}

/** 把可嵌套的 CooFX 绘制调用合并为一次状态快照。 */
internal class CooFxRenderStateScope<State>(
    private val capture: () -> State,
    private val restore: (State) -> Unit,
) {
    private var depth = 0

    fun <T> use(block: () -> T): T {
        if (depth != 0) return block()
        val state = capture()
        depth = 1
        try {
            return block()
        } finally {
            depth = 0
            restore(state)
        }
    }
}
