package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleIrisRenderStageContractTest {
    @Test
    fun `gpu particles render inside the platform particle engine pass`() {
        val fabricMixin = readProjectFile(
            "fabric/src/main/java/cn/coostack/cooparticlesapi/mixin/CParticleEngineFabricMixin.java"
        )
        val neoForgeMixin = readProjectFile(
            "neoforge/src/main/java/cn/coostack/cooparticlesapi/mixin/CParticleEngineNeoForgeMixin.java"
        )

        assertTrue("turnOffLightLayer()V" in fabricMixin)
        assertTrue("renderFabricParticlePass" in fabricMixin)
        assertTrue("turnOffLightLayer()V" in neoForgeMixin)
        assertTrue("renderTypeFilter" in neoForgeMixin)
        assertTrue("renderParticlePass" in neoForgeMixin)
    }

    /**
     * 校验 Iris MIXED 探测失败时，同一帧最多执行一次完整 GPU 粒子绘制。
     *
     * 示例：第一次 Fabric 粒子回调返回 `ALL`，后续回调返回 `NONE`。
     * 禁止在探测失败时让两次回调都返回 `ALL`，否则颜色与亮度会累加两次。
     */
    @Test
    fun `fabric fallback draws all gpu particles only once per frame`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )

        assertTrue("if (fabricParticlePassIndex++ == 0)" in manager)
    }

    @Test
    fun `world render events no longer draw gpu particles`() {
        val fabricClient = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt"
        )
        val neoForgeListener = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/listener/client/ClientEventsListener.kt"
        )

        assertFalse("CParticleSystemManager.renderWorld" in fabricClient)
        assertFalse("CParticleSystemManager.renderWorld" in neoForgeListener)
    }

    @Test
    fun `gpu draw expands instances before using the iris particle shader`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )
        val irisCompat = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/compat/IrisCompat.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )

        assertTrue("CParticleRenderer.render(" in manager)
        assertFalse("runWithFallbackFramebuffer" in manager)
        assertFalse("RenderSystem.getShader()" in manager)

        assertTrue("fun runWithParticleShader(" in irisCompat)
        assertTrue("getParticleTranslucentShader()" in irisCompat)
        assertTrue("particleShader.setDefaultUniforms(" in irisCompat)
        assertTrue("particleShader.apply()" in irisCompat)

        val expansion = renderer.indexOf("expandForParticleShader(")
        val irisScope = renderer.indexOf("IrisCompat.runWithParticleShader(")
        val expandedDraw = renderer.indexOf("drawExpanded(", irisScope)
        assertTrue(expansion >= 0)
        assertTrue(irisScope > expansion)
        assertTrue(expandedDraw > irisScope)
        assertFalse("glDrawBuffer(" in renderer)
        assertFalse("glDrawBuffers(" in renderer)
    }

    @Test
    fun `iris particle shaders keep low alpha fragments`() {
        val shaderKeyMixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/compat/iris/ShaderKeyIrisCompatMixin.java"
        )
        val mixinConfig = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")

        assertTrue("net.irisshaders.iris.pipeline.programs.ShaderKey" in shaderKeyMixin)
        assertTrue("PARTICLES" in shaderKeyMixin)
        assertTrue("PARTICLES_TRANS" in shaderKeyMixin)
        assertTrue("0.001F" in shaderKeyMixin)
        assertTrue("compat.iris.ShaderKeyIrisCompatMixin" in mixinConfig)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(findRepoRoot().resolve(relativePath))
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
