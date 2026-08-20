package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.renderer.backend.RenderSceneTargets
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineOutputPort
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTarget
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineTextureSource
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingShaderAbi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
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
        assertTrue("uniform(CooTerrainMappingShaderAbi.BLACKNESS, CooUniformValue.FloatValue(1F))" in pipeline)
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
        assertTrue("mappingSignedDistance(relativePosition, progress)" in shader)
        assertTrue("shapeMask * clamp(Blackness" in shader)
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
        val composite = compiled.nodes.single { node -> node.name == "composite" }
        val coverageInput = composite.inputs.single { input ->
            input.sampler == CooTerrainMappingShaderAbi.CPARTICLE_COVERAGE_MASK
        }
        assertTrue(CooTerrainMappingShaderAbi.HAS_CPARTICLE_COVERAGE in composite.uniforms)
        assertTrue(compiled.lines.any { line ->
            line.input == coverageInput &&
                (line.output as? CooPipelineTextureSource.FramebufferColor)?.target ==
                RenderSceneTargets.CPARTICLE_COVERAGE_MASK
        })
        assertTrue(compiled.lines.any { line ->
            line.input == CooPipelineTarget.FinalScreen &&
                (line.output as? CooPipelineOutputPort)?.node == composite.name
        })
    }

    private fun source(path: String): String {
        val requested = Path(path)
        if (requested.exists()) return requested.readText()
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (candidate.exists()) return candidate.readText()
            cursor = cursor.parent
        }
        return requested.readText()
    }
}
