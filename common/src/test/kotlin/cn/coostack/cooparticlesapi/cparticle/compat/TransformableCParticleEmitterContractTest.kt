package cn.coostack.cooparticlesapi.cparticle.compat

import java.nio.file.Files
import java.nio.file.Path
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertTrue

class TransformableCParticleEmitterContractTest {
    @Test
    fun `local particle geometry transform is isolated from old systems`() {
        val systemSource = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val bridgeSource = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/TransformableCParticleEmitterBridge.kt"
        )
        val vertexSource = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )

        assertTrue("internal var transformsSimulatedParticleSpace = false" in systemSource)
        assertTrue("system.transformsSimulatedParticleSpace = true" in bridgeSource)
        assertTrue("system.transformsSimulatedParticleSpace = false" in bridgeSource)
        assertTrue("if (uTransformParticleGeometry != 0)" in vertexSource)
        assertTrue("if (uTransformParticleGeometry != 0)" in vertexSource)
        assertTrue("offset *= mix(previousScale, currentScale, uPartial)" in vertexSource)
        assertTrue("uPrevGroupMat * vec4(offset, 0.0)" !in vertexSource)
    }

    @Test
    fun `cparticle renderer enables back face culling`() {
        val rendererSource = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val vertexSource = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )

        assertTrue("glEnable(GL_CULL_FACE)" in rendererSource)
        assertTrue("glCullFace(GL_BACK)" in rendererSource)
        assertTrue("glFrontFace(GL_CCW)" in rendererSource)
        assertTrue("glCullFace(cullFaceMode)" in rendererSource)
        assertTrue("glFrontFace(frontFaceMode)" in rendererSource)
        assertTrue("if (cullEnabled) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)" in rendererSource)
        assertTrue("BILLBOARD 保持固定正面绕序" in vertexSource)
        assertTrue("bool reverseWinding = mode != 0 && dot(facingNormal, toCamera) < 0.0" in vertexSource)
        assertTrue("if (reverseWinding)" in vertexSource)
        assertTrue("GL31.glDrawArraysInstanced(GL_TRIANGLES, 0, EXPANDED_VERTICES_PER_PARTICLE, instances)" in source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleGlBuffer.kt"
        ))

        val cameraLeft = Vector3f(-1f, 0f, 0f)
        val cameraUp = Vector3f(0f, 1f, 0f)
        val toCamera = Vector3f(0f, 0f, 1f)
        val corners = arrayOf(
            Vector3f(cameraLeft).negate().sub(cameraUp),
            Vector3f(cameraLeft).sub(cameraUp),
            Vector3f(cameraLeft).negate().add(cameraUp),
            Vector3f(cameraLeft).add(cameraUp),
        )
        listOf(0, 2, 1, 1, 2, 3).chunked(3).forEach { triangle ->
            val firstEdge = Vector3f(corners[triangle[1]]).sub(corners[triangle[0]])
            val secondEdge = Vector3f(corners[triangle[2]]).sub(corners[triangle[0]])
            assertTrue(firstEdge.cross(secondEdge).dot(toCamera) > 0f)
        }
    }

    @Test
    fun `manager registers auto emitter and releases its systems`() {
        val managerSource = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmittersManager.kt"
        )

        assertTrue("AutoTransformableCParticleEmitter::class.java.isAssignableFrom(clazz)" in managerSource)
        assertTrue("generateTransformableCParticleEmitterCodec" in managerSource)
        assertTrue("emitter.finishClientSystems()" in managerSource)
        assertTrue("emitters.world = viewWorld" in managerSource)
    }

    @Test
    fun `local and world systems keep separate lifecycles`() {
        val bridgeSource = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/TransformableCParticleEmitterBridge.kt"
        )

        assertTrue("this.space == space" in bridgeSource)
        assertTrue("/${'$'}spaceName/" in bridgeSource)
        assertTrue("system.groupTransform.identity()" in bridgeSource)
        assertTrue("releaseSystemsWhenEmpty(\"transformable_emitter/${'$'}emitterId/\")" in bridgeSource)
    }

    private fun source(path: String): String = Files.readString(findRepoRoot().resolve(path))

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
