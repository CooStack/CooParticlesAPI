package cn.coostack.cooparticlesapi.test.options.renderer

import java.nio.file.Path
import kotlin.io.path.Path as pathOf
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbitalRailgunPostEffectContractTest {
    @Test
    fun `block test orbital railgun uses terrain mask bloom and chromatic composite`() {
        val options = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/PostEffectDemoOptions.kt"
        )
        val strike = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/orbital_railgun_strike.fsh"
        )
        val composite = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/orbital_railgun_composite.fsh"
        )
        val terrain = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/orbital_railgun.fsh"
        )
        val terrainPipeline = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/OrbitalRailgunTerrain.kt"
        )
        val backend = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/OpenGlPostEffectExecutionBackend.kt"
        )
        val blockGroup = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/builtin/BlockAPITestGroupBuilder.kt"
        )
        val apiGroup = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/APITestGroupBuilder.kt"
        )

        assertTrue("ORBITAL_RAILGUN = CooShaderEffects.register" in options)
        assertFalse("input(\"depth\", optional = true)" in options)
        assertFalse("pingPong(\"blur\", iterations = 2" in options)
        assertTrue("vertex(screenVertex)" in options)
        assertTrue("uniform(\"cooEffectCenterX\", target.x)" in options)
        assertTrue("line(strike.color(), composite.input(\"scene\"))" in options)
        assertTrue("OrbitalRailgunBlockTestOption(player)" in blockGroup)
        assertFalse("PostEffectDemoOptions.orbitalRailgun(player)" in apiGroup)

        val blockOption = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/OrbitalRailgunBlockTestOption.kt"
        )
        assertTrue("DemoLightOrbRenderEntity" in blockOption)
        assertTrue("DemoMaskBloomStraightLaserRenderEntity" in blockOption)
        assertTrue("OrbitalRailgunTerrain.pipeline" in blockOption)
        assertTrue("CooTerrainEffectManager.apply" in blockOption)
        assertTrue("surfaceSnapshot" in blockOption)
        assertTrue("removePositions" in blockOption)
        assertTrue("CooTerrainEffectManager.append" in blockOption)
        assertTrue("cooEffectCenterRelative" in strike)
        assertTrue("effectVisible" in strike)
        assertTrue("darkness" in strike)
        assertTrue("cooViewProjection" in composite)
        assertTrue("effectVisible" in composite)
        assertTrue("chromaticOffset" in composite)
        assertFalse("texture(bloom, screen_uv)" in composite)
        assertTrue("postInScene()" in terrainPipeline)
        assertTrue("maskOutput()" in terrainPipeline)
        assertTrue("OrbitalCenter" in terrain)
        assertTrue("MaskColor" in terrain)

        assertTrue("resolveSceneDepthTexture(step, input)" in backend)
        assertTrue("program.setMatrix4(\"cooViewProjection\", viewProjection)" in backend)
        assertTrue("program.setMatrix4(\"cooInverseViewProjection\", inverseViewProjection)" in backend)
        assertTrue("\"cooEffectCenterRelative\"" in backend)
    }

    private fun readProjectFile(relativePath: String): String {
        return projectFile(relativePath).readText()
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = pathOf(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (cursor.resolve("settings.gradle").exists()) {
                return cursor.resolve(relativePath)
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
