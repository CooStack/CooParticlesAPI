package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegionType

/** 为程序化 Mapping 测试注册 terrain-only 屏幕遮罩管线和模板。 */
internal object ProceduralTerrainMappingTerrain {
    private const val PIPELINE_PATH = "test/procedural_terrain_mapping"
    private const val MAPPING_PATH = "test/procedural_terrain_mapping"

    val pipeline = CooPipelines.block(ofID(PIPELINE_PATH)) {
        postInScene()
        screenOnly()
        val composite = pass("composite") {
            vertex(ofID("pipeline/vertexes/procedural_mapping_screen.vsh"))
            fragment(ofID("post/procedural_mapping_screen.fsh"))
            inputSceneColor("SceneColor")
            inputSceneDepth("SceneDepth")
            inputSceneDepthNoHand("SceneDepthNoHand")
            inputTerrainOpaqueDepth("TerrainOpaqueDepth")
            inputTerrainTranslucentDepthBefore("TerrainTranslucentDepthBefore")
            inputTerrainTranslucentDepthAfter("TerrainTranslucentDepthAfter")
            outputFormat(CooTextureFormat.RGBA8)
            uniform("CooMappingRegion", CooUniformValue.Vec4Value(0F, 0F, 0F, 0F))
            uniform("CooMappingProgress", CooUniformValue.FloatValue(0F))
            uniform("Blackness", CooUniformValue.FloatValue(1F))
            uniform("Feather", CooUniformValue.FloatValue(0.06F))
        }

        line(composite.color(), screenTarget())
    }

    val mapping = CooTerrainMappingManager.register(
        ofID(MAPPING_PATH),
        pipeline,
        CooTerrainMappingRegionType.SPHERE
    )

    /** 触发模板在两端初始化阶段完成一次性注册。 */
    fun ensureRegistered() {
        mapping
    }
}
