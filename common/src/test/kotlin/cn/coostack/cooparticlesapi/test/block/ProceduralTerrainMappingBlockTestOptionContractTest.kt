package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 锁定程序化 Mapping BlockTest 的 screen-only 管线、GPU 动画和资源边界。 */
class ProceduralTerrainMappingBlockTestOptionContractTest {
    @Test
    fun `option creates one mapping instance without per tick section rebuild updates`() {
        val source = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingBlockTestOption.kt")
        assertTrue("CooTerrainMappingManager.create(" in source)
        assertTrue("duration(TOTAL_TICKS)" in source)
        assertTrue("TOTAL_TICKS = 240L" in source)
        assertTrue("CooTerrainMappingManager.remove" in source)
        assertTrue("composition(CooTerrainEffectComposition.ADDITIVE)" in source)
        assertFalse("uniforms(" in source)
        assertFalse("CooTerrainMappingManager.updateRegion(" in source)
        assertFalse("CooTerrainMappingManager.updateUniforms(" in source)
        assertFalse("CooTerrainEffectManager" in source)
        assertFalse("Heightmap" in source)
        assertFalse("BlockPos" in source)
        assertTrue("player.position()" in source)
    }

    @Test
    fun `pipeline uses a terrain-only screen mask without replaying world geometry`() {
        val pipeline = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingTerrain.kt")
        val shader = source("common/src/main/resources/assets/cooparticlesapi/shaders/post/procedural_mapping_screen.fsh")
        assertTrue("CooTerrainMappingManager.register" in pipeline)
        assertTrue("postInScene()" in pipeline)
        assertTrue("screenOnly()" in pipeline)
        assertTrue("inputSceneColor(\"SceneColor\")" in pipeline)
        assertTrue("inputSceneDepth(\"SceneDepth\")" in pipeline)
        assertTrue("inputSceneDepthNoHand(\"SceneDepthNoHand\")" in pipeline)
        assertTrue("inputTerrainOpaqueDepth(\"TerrainOpaqueDepth\")" in pipeline)
        assertTrue("inputTerrainTranslucentDepthBefore(\"TerrainTranslucentDepthBefore\")" in pipeline)
        assertTrue("inputTerrainTranslucentDepthAfter(\"TerrainTranslucentDepthAfter\")" in pipeline)
        assertTrue("vertexes/procedural_mapping_screen.vsh" in pipeline)
        assertTrue("uniform(\"Blackness\", CooUniformValue.FloatValue(1F))" in pipeline)
        assertFalse("world(\"geometry\")" in pipeline)
        assertFalse("BaseSampler" in pipeline)

        assertTrue("#version 150" in shader)
        assertTrue("uniform sampler2D SceneColor" in shader)
        assertTrue("uniform sampler2D SceneDepth" in shader)
        assertTrue("uniform sampler2D SceneDepthNoHand" in shader)
        assertTrue("uniform sampler2D TerrainOpaqueDepth" in shader)
        assertTrue("uniform sampler2D TerrainTranslucentDepthBefore" in shader)
        assertTrue("uniform sampler2D TerrainTranslucentDepthAfter" in shader)
        assertTrue("visibleOpaqueTerrain" in shader)
        assertTrue("visibleTranslucentTerrain" in shader)
        assertTrue("handDepthChanged" in shader)
        assertTrue("uniform mat4 cooInverseViewProjection" in shader)
        assertTrue("distance(relativePosition, CooMappingRegion.xyz)" in shader)
        assertTrue("circularMask * clamp(Blackness" in shader)
        assertTrue("mix(scene.rgb, vec3(0.0), mask)" in shader)
    }

    @Test
    fun `pipeline compiles as scene post without a world node`() {
        val compiled = CooPipelineCompiler.compile(ProceduralTerrainMappingTerrain.pipeline)

        assertTrue(compiled.nodes.none { it.name == "geometry" })
        assertTrue(compiled.nodes.any { it.name == "composite" })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "SceneColor"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "SceneDepth"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "SceneDepthNoHand"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "TerrainOpaqueDepth"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "TerrainTranslucentDepthBefore"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "TerrainTranslucentDepthAfter"
        })
    }

    private fun source(path: String): String {
        val requested = Path.of(path)
        if (Files.exists(requested)) return Files.readString(requested)
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (Files.exists(candidate)) return Files.readString(candidate)
            cursor = cursor.parent
        }
        return Files.readString(requested)
    }
}
