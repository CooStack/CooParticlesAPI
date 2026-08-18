package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegionType

/** 为程序化 Mapping 测试注册地形 bloom 管线和模板。 */
internal object ProceduralTerrainMappingTerrain {
    private const val PIPELINE_PATH = "test/procedural_terrain_mapping"
    private const val MAPPING_PATH = "test/procedural_terrain_mapping"

    val pipeline = CooPipelines.block(ofID(PIPELINE_PATH)) {
        postInScene()
        val geometry = world("geometry") {
            shader(ofID("terrain/procedural_mapping_bloom"))
            inputBlockAtlas("BaseSampler")
            inputSceneColor("SceneColor", optional = true)
            maskOutput()
            outputFormat(CooTextureFormat.RGBA16F)
            uniform("RingRadius", CooUniformValue.FloatValue(4F))
            uniform("RingWidth", CooUniformValue.FloatValue(0.8F))
            uniform("RingIntensity", CooUniformValue.FloatValue(0F))
            uniform("RingColor", CooUniformValue.Vec3Value(0.32F, 0.82F, 1F))
            uniform("RingProgress", CooUniformValue.FloatValue(0F))
        }
        val extract = pass("bloom_extract") {
            fragment(ofID("post/bloom_bright_extract.fsh"))
            input("scene", format = CooTextureFormat.RGBA16F)
            outputFormat(CooTextureFormat.RGBA16F)
            mipLevels(12)
            uniform("threshold", 0F)
            uniform("softKnee", 0.5F)
            uniform("PremultipliedInput", CooUniformValue.BoolValue(true))
            uniform("Intensity", 3F)
        }
        val bloomBslAtlas = pass("bloom_bsl_atlas") {
            fragment(ofID("post/bloom_bsl_atlas.fsh"))
            input("BloomInput", format = CooTextureFormat.RGBA16F, mipLevels = 12)
            outputFormat(CooTextureFormat.RGBA16F)
            uniform("BloomLevels", CooUniformValue.IntValue(7))
        }
        val composite = pass("composite") {
            fragment(ofID("post/mask_bloom_composite.fsh"))
            input("SceneColor")
            input("BloomAtlas", format = CooTextureFormat.RGBA16F)
            uniform("MipLevels", CooUniformValue.IntValue(7))
            outputFormat(CooTextureFormat.RGBA8)
        }

        line(geometry.color(), worldTarget())
        line(geometry.mask(), extract.input("scene"))
        line(extract.color(), bloomBslAtlas.input("BloomInput"))
        line(sceneColor(), composite.input("SceneColor"))
        line(bloomBslAtlas.color(), composite.input("BloomAtlas"))
        line(composite.color(), screenTarget())

        parameter("bloomThreshold", extract, "threshold")
        parameter("bloomSoftKnee", extract, "softKnee")
        parameter("intensity", extract, "Intensity")
        parameter("bloomMipLevels", bloomBslAtlas, "BloomLevels")
        parameter("bloomMipLevels", composite, "MipLevels")
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
