package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation

/**
 * 已将 uniform 应用到 Pipeline 模板后的缓存键。
 *
 * @property pipelineId 原始 Pipeline ID
 * @property uniforms 参与 Pipeline 配置的完整 uniform 映射；键为 uniform 名称，值为对应数据
 */
internal data class PipelineConfigKey(
    val pipelineId: ResourceLocation,
    val uniforms: Map<String, CooUniformValue>
)
