package cn.coostack.cooparticlesapi.test.options.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation

/**
 * 保存 FBO 示例在 Pipeline 和 terrain sampler 之间共享的资源 ID。
 */
internal object TerrainFboExampleIds {
    /** 星空生成 pass 的命名颜色附件。 */
    val STARFIELD_TARGET: ResourceLocation = id("example/fbo/starfield")

    /** 把示例路径转换为模组命名空间下的资源 ID。 */
    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
