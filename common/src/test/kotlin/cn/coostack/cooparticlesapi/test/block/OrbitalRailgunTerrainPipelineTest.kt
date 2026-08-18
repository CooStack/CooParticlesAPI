package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelineCompiler
import kotlin.test.Test
import kotlin.test.assertTrue

class OrbitalRailgunTerrainPipelineTest {
    @Test
    fun `terrain orbital pipeline compiles world mask and scene post graph`() {
        val compiled = CooPipelineCompiler.compile(OrbitalRailgunTerrain.pipeline)

        assertTrue(compiled.nodes.any { it.name == "geometry" })
        assertTrue(compiled.nodes.any { it.name == "bloom_extract" })
        assertTrue(compiled.nodes.any { it.name == "bloom_bsl_atlas" })
        assertTrue(compiled.nodes.any { it.name == "composite" })
        assertTrue(RenderFrameStage.WORLD_PASS in compiled.stages)
        assertTrue(RenderFrameStage.SCENE_POST in compiled.stages)
        assertTrue(RenderBackendCapability.FINAL_FRAME_POST in compiled.requiredCapabilities)
        assertTrue(RenderBackendCapability.SAFE_WORLD_COMPOSITE in compiled.requiredCapabilities)
        assertTrue(compiled.attachments.any { it.output.node == "geometry" && it.output.attachment == 1 })
    }
}
