package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import com.mojang.renderpearl.api.pipeline.RenderPipeline

/**
 * 为两个加载器选择描述符要求的不可变管线。
 * 原版管线缓存负责实际 GPU 程序生命周期，Iris 通道标记由 RenderType 包装负责。
 */
object CooShaderStateResolver {
    /**
     * 解析着色程序来源，不以基础管线替代缺失的自定义程序。
     * 示例：`pipeline(descriptor)`。
     * @param descriptor 已验证的渲染描述符
     * @return 包含程序、绑定布局和目标格式的源管线
     */
    fun pipeline(descriptor: CooRenderTypeDescriptor): RenderPipeline =
        when (descriptor.shaderPreset) {
            CooRenderTypeShaderPreset.POSITION_COLOR -> MCShaders.POSITION_COLOR
            CooRenderTypeShaderPreset.COO_GLOW -> MCShaders.GLOW
            CooRenderTypeShaderPreset.CUSTOM -> requireNotNull(descriptor.customShader) {
                "CooRenderTypeDescriptor(name=${descriptor.name}) shaderPreset=CUSTOM but customShader is null"
            }.invoke()
        }
}
