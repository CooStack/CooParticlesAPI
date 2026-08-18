package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineInputPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 锁定程序化 Mapping BlockTest 的实例复用、阶段更新和资源边界。 */
class ProceduralTerrainMappingBlockTestOptionContractTest {
    @Test
    fun `option reuses one mapping instance across animation phases`() {
        val source = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingBlockTestOption.kt")
        assertTrue("CooTerrainMappingManager.create(" in source)
        assertTrue("CooTerrainMappingManager.updateRegion(" in source)
        assertTrue("CooTerrainMappingManager.updateUniforms(" in source)
        assertTrue("duration(TOTAL_TICKS)" in source)
        assertTrue("EXPAND_TICKS" in source)
        assertTrue("SUSTAIN_TICKS" in source)
        assertTrue("SHRINK_TICKS" in source)
        assertTrue("CooTerrainMappingManager.remove" in source)
        assertTrue("composition(CooTerrainEffectComposition.ADDITIVE)" in source)
        assertFalse("CooTerrainEffectManager" in source)
        assertFalse("Heightmap" in source)
        assertFalse("BlockPos" in source)
        assertTrue("player.position()" in source)
    }

    @Test
    fun `pipeline and shader expose terrain mask bloom`() {
        val pipeline = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/ProceduralTerrainMappingTerrain.kt")
        val shader = source("common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/procedural_mapping_bloom.fsh")
        val composite = source("common/src/main/resources/assets/cooparticlesapi/shaders/post/procedural_mapping_bloom_composite.fsh")
        assertTrue("CooTerrainMappingManager.register" in pipeline)
        assertTrue("maskOutput()" in pipeline)
        assertTrue("bloom_bright_extract.fsh" in pipeline)
        assertTrue("bloom_bsl_atlas.fsh" in pipeline)
        assertTrue("procedural_mapping_bloom_composite.fsh" in pipeline)
        assertTrue("EffectColor" in pipeline)
        assertTrue("mipLevels(4)" in pipeline)
        assertTrue("CooUniformValue.IntValue(3)" in pipeline)
        assertTrue("#version 330 core" in shader)
        assertTrue("layout(location = 1) out vec4 MaskColor" in shader)
        assertTrue("BaseSampler" in shader)
        assertTrue("RingRadius" in shader)
        assertTrue("CooMappingComposition == 2" in shader)
        assertTrue("ring <= 0.0" in shader)
        assertTrue("CooMappingRegion.w + max(RingWidth" in shader)
        assertFalse("CooMappingRegion.w * 0.88" in shader)
        assertTrue("effectAlpha" in shader)
        assertFalse("CooMappingDepthAvailable == 0" in shader)
        assertTrue("emissive * fogFade" in shader)
        assertFalse("mix(emissive, FogColor" in shader)
        assertTrue("EffectColor" in composite)
        assertTrue("bloomResult + effectCore" in composite)
        assertFalse("#moj_import" in shader)
    }

    @Test
    fun `pipeline captures the additive core before bloom composition`() {
        val compiled = CooPipelineCompiler.compile(ProceduralTerrainMappingTerrain.pipeline)

        assertTrue(compiled.nodes.any { it.name == "geometry" })
        assertTrue(compiled.nodes.any { it.name == "composite" })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "SceneColor"
        })
        assertTrue(compiled.lines.any { line ->
            (line.input as? CooPipelineInputPort)?.sampler == "EffectColor"
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
