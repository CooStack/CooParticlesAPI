package cn.coostack.cooparticlesapi.coofx.client.render

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledMaterial
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledPrimitive
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxIndexType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexComponentType
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxVertexSemantic
import cn.coostack.cooparticlesapi.coofx.render.gpu.CooFxGpuPackage
import cn.coostack.cooparticlesapi.coofx.render.gpu.CooFxGpuPackageKey
import cn.coostack.cooparticlesapi.coofx.render.gpu.CooFxGpuPackageUploader
import cn.coostack.cooparticlesapi.coofx.runtime.mesh.render.CooFxMeshDrawBinding
import org.lwjgl.opengl.GL33.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL33.GL_ARRAY_BUFFER_BINDING
import org.lwjgl.opengl.GL33.GL_ELEMENT_ARRAY_BUFFER
import org.lwjgl.opengl.GL33.GL_FLOAT
import org.lwjgl.opengl.GL33.GL_STATIC_DRAW
import org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL33.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL33.GL_UNSIGNED_SHORT
import org.lwjgl.opengl.GL33.GL_VERTEX_ARRAY_BINDING
import org.lwjgl.opengl.GL33.glBindBuffer
import org.lwjgl.opengl.GL33.glBindVertexArray
import org.lwjgl.opengl.GL33.glBufferData
import org.lwjgl.opengl.GL33.glDeleteBuffers
import org.lwjgl.opengl.GL33.glDeleteVertexArrays
import org.lwjgl.opengl.GL33.glEnableVertexAttribArray
import org.lwjgl.opengl.GL33.glGenBuffers
import org.lwjgl.opengl.GL33.glGenVertexArrays
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glIsVertexArray
import org.lwjgl.opengl.GL33.glVertexAttribPointer
import org.lwjgl.system.MemoryUtil

internal data class CooFxOpenGlPrimitive(
    val drawBinding: CooFxMeshDrawBinding,
    val material: CooFxCompiledMaterial,
)

/** 只拥有一个 CooFX compiled package 对应的静态 VAO、VBO 和 EBO。 */
internal class CooFxOpenGlGpuPackage(
    override val key: CooFxGpuPackageKey,
    override val generation: Long,
    private val primitives: Map<String, CooFxOpenGlPrimitive>,
    private val vertexArrays: IntArray,
    private val vertexBuffers: IntArray,
    private val indexBuffers: IntArray,
) : CooFxGpuPackage {
    private var released = false

    fun primitive(primitiveId: String): CooFxOpenGlPrimitive {
        check(!released) { "CooFX GPU package 已经释放" }
        return requireNotNull(primitives[primitiveId]) { "找不到 GPU primitive：$primitiveId" }
    }

    override fun release() {
        if (released) return
        released = true
        vertexArrays.filter { it > 0 }.forEach(::glDeleteVertexArrays)
        vertexBuffers.filter { it > 0 }.forEach(::glDeleteBuffers)
        indexBuffers.filter { it > 0 }.forEach(::glDeleteBuffers)
    }
}

/** 在当前渲染线程把不可变 compiled primitive 上传为静态 OpenGL 资源。 */
internal class CooFxOpenGlGpuPackageUploader : CooFxGpuPackageUploader {
    override fun upload(
        compiledPackage: CooFxCompiledRenderPackage,
        key: CooFxGpuPackageKey,
        generation: Long,
    ): CooFxGpuPackage {
        val previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val vertexArrays = IntArray(compiledPackage.primitives.size)
        val vertexBuffers = IntArray(compiledPackage.primitives.size)
        val indexBuffers = IntArray(compiledPackage.primitives.size)
        val uploaded = LinkedHashMap<String, CooFxOpenGlPrimitive>()
        try {
            compiledPackage.primitives.forEachIndexed { index, primitive ->
                val vertexArray = glGenVertexArrays()
                val vertexBuffer = glGenBuffers()
                val indexBuffer = glGenBuffers()
                vertexArrays[index] = vertexArray
                vertexBuffers[index] = vertexBuffer
                indexBuffers[index] = indexBuffer

                glBindVertexArray(vertexArray)
                glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
                uploadBytes(GL_ARRAY_BUFFER, primitive.vertexBytes.copyBytes())
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
                uploadBytes(GL_ELEMENT_ARRAY_BUFFER, primitive.indexBytes.copyBytes())
                primitive.vertexLayout.attributes.forEach { attribute ->
                    val location = attribute.semantic.shaderLocation()
                    glVertexAttribPointer(
                        location,
                        attribute.componentCount,
                        attribute.componentType.glType(),
                        attribute.componentType != CooFxVertexComponentType.FLOAT,
                        primitive.vertexLayout.strideBytes,
                        attribute.byteOffset.toLong(),
                    )
                    glEnableVertexAttribArray(location)
                }
                uploaded[primitive.id] = CooFxOpenGlPrimitive(
                    drawBinding = primitive.drawBinding(vertexArray),
                    material = compiledPackage.materials[primitive.materialIndex],
                )
            }
            return CooFxOpenGlGpuPackage(
                key = key,
                generation = generation,
                primitives = uploaded,
                vertexArrays = vertexArrays,
                vertexBuffers = vertexBuffers,
                indexBuffers = indexBuffers,
            )
        } catch (failure: Throwable) {
            vertexArrays.filter { it > 0 }.forEach(::glDeleteVertexArrays)
            vertexBuffers.filter { it > 0 }.forEach(::glDeleteBuffers)
            indexBuffers.filter { it > 0 }.forEach(::glDeleteBuffers)
            throw failure
        } finally {
            glBindVertexArray(if (previousVertexArray == 0 || glIsVertexArray(previousVertexArray)) previousVertexArray else 0)
            glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer)
        }
    }

    private fun uploadBytes(target: Int, bytes: ByteArray) {
        val buffer = MemoryUtil.memAlloc(bytes.size)
        try {
            buffer.put(bytes).flip()
            glBufferData(target, buffer, GL_STATIC_DRAW)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun CooFxCompiledPrimitive.drawBinding(vertexArray: Int): CooFxMeshDrawBinding = CooFxMeshDrawBinding(
        vertexArrayObject = vertexArray,
        indexCount = drawRange.indexCount,
        indexType = when (indexType) {
            CooFxIndexType.UNSIGNED_SHORT -> GL_UNSIGNED_SHORT
            CooFxIndexType.UNSIGNED_INT -> GL_UNSIGNED_INT
        },
        indexByteOffset = drawRange.firstIndex.toLong() * indexType.byteSize,
        firstInstanceAttributeLocation = 4,
    )

    private fun CooFxVertexSemantic.shaderLocation(): Int = when (this) {
        CooFxVertexSemantic.POSITION -> 0
        CooFxVertexSemantic.NORMAL -> 1
        CooFxVertexSemantic.TEXCOORD_0 -> 2
        CooFxVertexSemantic.COLOR_0 -> 3
    }

    private fun CooFxVertexComponentType.glType(): Int = when (this) {
        CooFxVertexComponentType.FLOAT -> GL_FLOAT
        CooFxVertexComponentType.UNSIGNED_BYTE_NORMALIZED -> GL_UNSIGNED_BYTE
        CooFxVertexComponentType.UNSIGNED_SHORT_NORMALIZED -> GL_UNSIGNED_SHORT
    }
}
