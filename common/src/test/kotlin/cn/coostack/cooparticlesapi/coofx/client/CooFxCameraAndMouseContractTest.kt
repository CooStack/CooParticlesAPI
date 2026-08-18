package cn.coostack.cooparticlesapi.coofx.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CooFxCameraAndMouseContractTest {
    @Test
    fun `camera claim removes stale pose and suppresses mouse at input boundary`() {
        val registry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxSceneClientRegistry.kt"
        )
        val tracking = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxCameraTrackingManager.kt"
        )
        val mouseMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/CooFxCameraMouseMixin.java"
        )
        val rendererMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/GameRendererCooFxCameraMixin.java"
        )
        val meshRenderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/" +
                "CooFxMeshParticleRenderer.kt"
        )
        val gpuPackage = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/render/" +
                "CooFxOpenGlGpuPackage.kt"
        )
        val particleBuffer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleGlBuffer.kt"
        )
        val lifecycle = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )

        assertTrue("CooFxCameraTrackingManager.remove(entity.uuid)" in registry)
        assertTrue("CooFxCameraTrackingManager.isMouseSuppressed()" in mouseMixin)
        assertTrue("cancellable = true" in mouseMixin)
        assertTrue("CooFxSceneClientRegistry.clear()" in lifecycle)
        assertTrue("fun isMouseSuppressed(): Boolean" in tracking)
        assertTrue("fun isVanillaCameraEffectsSuppressed(): Boolean" in tracking)
        assertTrue("method = \"bobView\"" in rendererMixin)
        assertTrue("method = \"bobHurt\"" in rendererMixin)
        assertTrue("Camera;setup" in rendererMixin)
        assertTrue("isVanillaCameraEffectsSuppressed" in rendererMixin)
        assertTrue("method = \"renderItemInHand\"" in rendererMixin)
        assertTrue("cooParticlesAPI\$suppressCooFxFirstPersonHands" in rendererMixin)
        assertTrue("callback.cancel();" in rendererMixin)
        assertTrue("enabledAttributes = null" in meshRenderer)
        assertTrue("restoresVertexArrayState" in meshRenderer)
        assertTrue("glGetError" !in particleBuffer)
        assertTrue("[DEBUG-cparticle-iris]" !in particleBuffer)
        assertTrue("restoreVertexArray(previousVao)" in particleBuffer)
        assertTrue("glIsVertexArray(vertexArrayObject)" in particleBuffer)
        assertTrue("glIsVertexArray" in meshRenderer)
        val bindBatch = meshRenderer.substringAfter("private fun bindBatch").substringBefore("private fun <T> withDrawState")
        assertTrue("check(glIsVertexArray(binding.vertexArrayObject))" in bindBatch)
        assertTrue("vertex array object is no longer valid" in bindBatch)
        assertTrue("restoreVertexArray" in meshRenderer)
        assertTrue("glIsVertexArray(previousVertexArray)" in gpuPackage)
    }

    @Test
    fun `iris expansion records interleaved new entity stride and exact vertex count`() {
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/runtime/mesh/render/" +
                "CooFxMeshParticleRenderer.kt"
        )
        val shader = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/render/CooFxShaderProgram.kt"
        )
        val vertexShader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/coofx/mesh_particle.vsh"
        )

        assertTrue("glGetTransformFeedbackVarying" in renderer)
        assertTrue("DefaultVertexFormat.NEW_ENTITY.vertexSize" in renderer)
        assertTrue("expandedEntityVertexCount = vertexCount" in renderer)
        assertTrue("transformFeedbackVaryings(" in shader)
        assertTrue("tfEntityPosition = cameraRelativePosition" in vertexShader)
         assertTrue("uIrisEntitySpace ? worldPosition" !in vertexShader)
    }

    private fun readProjectFile(relativePath: String): String =
        Files.readString(findRepoRoot().resolve(relativePath))

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("找不到项目根目录")
    }
}
