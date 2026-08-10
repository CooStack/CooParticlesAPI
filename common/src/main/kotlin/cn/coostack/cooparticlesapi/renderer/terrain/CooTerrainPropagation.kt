package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooRenderPipeline
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import cn.coostack.cooparticlesapi.extend.ofID

/** 传播测试只声明普通方块 Pipeline；每位置时间由 terrain 顶点数据交给 shader。 */
internal object CooTerrainPropagation {
    /** 传播效果共用的不可变 Pipeline 模板。 */
    val pipeline: CooRenderPipeline<BlockState> =
        CooPipelines.block(ofID("internal/terrain_propagation")) {
            shader(ofID("terrain/propagation"))
            inputBlockAtlas("BaseSampler")
            inputSceneColor("SceneColor", optional = true)
            effectUv(CooEffectUvMode.FACE_LOCAL)
            uniform("TintStrength", 0F)
        }

}
