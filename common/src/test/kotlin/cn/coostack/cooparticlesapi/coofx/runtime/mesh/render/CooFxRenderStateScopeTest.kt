package cn.coostack.cooparticlesapi.coofx.runtime.mesh.render

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CooFxRenderStateScopeTest {
    @Test
    fun `nested draw calls capture and restore state once`() {
        var captures = 0
        val restores = mutableListOf<String>()
        val scope = CooFxRenderStateScope(
            capture = {
                captures++
                "caller"
            },
            restore = restores::add,
        )

        scope.use {
            scope.use { }
            scope.use { }
        }

        assertEquals(1, captures)
        assertEquals(listOf("caller"), restores)
    }

    @Test
    fun `failed nested draw restores outer state once`() {
        var captures = 0
        val restores = mutableListOf<String>()
        val scope = CooFxRenderStateScope(
            capture = {
                captures++
                "caller"
            },
            restore = restores::add,
        )

        assertFailsWith<IllegalStateException> {
            scope.use {
                scope.use {
                    error("draw failed")
                }
            }
        }

        assertEquals(1, captures)
        assertEquals(listOf("caller"), restores)
    }

    @Test
    fun `frame upload allocator assigns non-overlapping offsets`() {
        val allocator = CooFxFrameUploadAllocator()
        allocator.prepare(instanceBytes = 160L, nodeMatrixBytes = 64L)

        assertEquals(CooFxFrameUploadRange(0L, 0L), allocator.reserve(64L, 16L))
        assertEquals(CooFxFrameUploadRange(64L, 16L), allocator.reserve(96L, 48L))
        assertFailsWith<IllegalStateException> {
            allocator.reserve(4L, 0L)
        }

        allocator.clear()
        assertFailsWith<IllegalStateException> {
            allocator.reserve(0L, 0L)
        }
    }

    @Test
    fun `default vao preserves generic current attributes without vao local operations`() {
        val renderer = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/" +
                    "CooFxMeshParticleRenderer.kt"
            )
        )
        val capture = renderer
            .substringAfter("private fun captureDrawState")
            .substringBefore("private fun readCurrentVertexAttribute")
        val genericAttributes = capture.substringBefore("if (!capturesVertexArrayState)")
        val defaultVao = capture
            .substringAfter("if (!capturesVertexArrayState)")
            .substringBefore("val attributeCount")
        val liveVao = capture.substringAfter("val attributeCount")
        val restore = renderer
            .substringAfter("private fun restoreDrawState")
            .substringBefore("private fun restoreVertexArray")

        assertTrue("vertexArrayObject != 0 && glIsVertexArray(vertexArrayObject)" in capture)
        assertTrue("readCurrentVertexAttribute(1, stack)" in genericAttributes)
        assertTrue("readCurrentVertexAttribute(2, stack)" in genericAttributes)
        assertTrue("readCurrentVertexAttribute(3, stack)" in genericAttributes)
        assertTrue("enabledAttributes = null" in defaultVao)
        assertTrue("attributeDivisors = null" in defaultVao)
        assertTrue("arrayBufferObject = glGetInteger(GL_ARRAY_BUFFER_BINDING)" in defaultVao)
        assertTrue("normal = normal" in defaultVao)
        assertTrue("textureCoordinates = textureCoordinates" in defaultVao)
        assertTrue("color = color" in defaultVao)
        assertTrue("glGetVertexAttribi" !in defaultVao)
        assertTrue("glEnableVertexAttribArray" !in defaultVao)
        assertTrue("glDisableVertexAttribArray" !in defaultVao)
        assertTrue("CParticleCapabilities.setVertexAttribDivisor" !in defaultVao)
        assertTrue("glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_ENABLED)" in liveVao)
        assertTrue("glGetVertexAttribi(location, GL_VERTEX_ATTRIB_ARRAY_DIVISOR)" in liveVao)
        assertTrue("glGetVertexAttribfv(location, GL_CURRENT_VERTEX_ATTRIB, values)" in renderer)
        assertTrue("restoresVertexArrayState && enabledAttributes != null && attributeDivisors != null" in restore)
        assertTrue("glBindBuffer(GL_ARRAY_BUFFER, state.arrayBufferObject)" in restore)
        assertTrue("glEnableVertexAttribArray(location)" in restore)
        assertTrue("glDisableVertexAttribArray(location)" in restore)
        assertTrue("state.normal?.let" in restore)
        assertTrue("state.textureCoordinates?.let" in restore)
        assertTrue("state.color?.let" in restore)
    }

    @Test
    fun `world pass captures renderer state outside the batch loop`() {
        val runtimeSource = Files.readString(
            findRepoRoot().resolve(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
            )
        )
        val outerStateScope = runtimeSource.substringBefore("private fun renderBatch")
        val batchRenderer = runtimeSource
            .substringAfter("private fun renderBatch")
            .substringBefore("private fun drawCooFxBatch")

        assertTrue("withRenderBatchStatePreserved(irisShaderPackActive)" in outerStateScope)
        assertTrue("particleRenderer.withPreservedDrawState" in outerStateScope)
        assertTrue("particleRenderer.prepareFrame(batches)" in outerStateScope)
        assertTrue("particleRenderer.finishFrame()" in outerStateScope)
        assertTrue("program.useOnContext" in outerStateScope)
        assertTrue("frameUniformsReady = true" in outerStateScope)
        assertTrue("withRenderBatchStatePreserved" !in batchRenderer)
        assertTrue("CooFxShaderUniforms(candidate.program)" in runtimeSource)
        assertTrue("private class CooFxShaderUniforms" in runtimeSource)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
