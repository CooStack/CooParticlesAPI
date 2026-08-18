package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.CooTextureFormat

/** 直接绘制地形环并把环的高亮输出接入 terrain bloom mask。 */
internal object OrbitalRailgunTerrain {
    val pipeline = CooPipelines.block(ofID("test/orbital_railgun_terrain")) {
        postInScene()
        val geometry = world("geometry") {
            shader(ofID("terrain/orbital_railgun"))
            inputBlockAtlas("BaseSampler")
            inputSceneColor("SceneColor", optional = true)
            maskOutput()
            outputFormat(CooTextureFormat.RGBA16F)
            uniform("OrbitalCenter", CooUniformValue.Vec3Value(0F, 0F, 0F))
            uniform("OrbitalRadius", 72F)
            uniform("OrbitalLineWidth", 0.55F)
            uniform("OrbitalColor", CooUniformValue.Vec3Value(0.58F, 0.94F, 1F))
            uniform("OrbitalDuration", 220F)
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
}
