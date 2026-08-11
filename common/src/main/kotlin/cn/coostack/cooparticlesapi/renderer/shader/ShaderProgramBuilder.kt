package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout

class ShaderProgramBuilder {
    private val delegate = AdvancedShaderProgramBuilder()

    /**
     * 在 `ShaderProgramBuilder` 中配置 `vertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vertex(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vertex(path: String): ShaderProgramBuilder {
        delegate.vertex(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun fragment(path: String): ShaderProgramBuilder {
        delegate.fragment(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `geometry`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`geometry(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun geometry(path: String): ShaderProgramBuilder {
        delegate.geometry(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `tessellationControl`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationControl(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationControl(path: String): ShaderProgramBuilder {
        delegate.tessellationControl(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `tessellationEvaluation`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationEvaluation(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationEvaluation(path: String): ShaderProgramBuilder {
        delegate.tessellationEvaluation(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `compute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`compute(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun compute(path: String): ShaderProgramBuilder {
        delegate.compute(path)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `vertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vertex(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vertex(shader: GlShader): ShaderProgramBuilder {
        delegate.vertex(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun fragment(shader: GlShader): ShaderProgramBuilder {
        delegate.fragment(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `geometry`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`geometry(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun geometry(shader: GlShader): ShaderProgramBuilder {
        delegate.geometry(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `tessellationControl`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationControl(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationControl(shader: GlShader): ShaderProgramBuilder {
        delegate.tessellationControl(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `tessellationEvaluation`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationEvaluation(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationEvaluation(shader: GlShader): ShaderProgramBuilder {
        delegate.tessellationEvaluation(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `compute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`compute(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun compute(shader: GlShader): ShaderProgramBuilder {
        delegate.compute(shader)
        return this
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `bufferLayout`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bufferLayout(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bufferLayout(layout: ShaderBufferLayout<*>): ShaderProgramBuilder {
        delegate.bufferLayout(layout)
        return this
    }

    /**
     * 声明链接前需要捕获的交错 transform-feedback 输出。
     *
     * 示例：`transformFeedbackVaryings("position", "uv")`。
     * 禁止传入 shader 未声明的变量名。
     *
     * @param names vertex/geometry shader 输出变量名
     * @return 当前 builder
     */
    fun transformFeedbackVaryings(vararg names: String): ShaderProgramBuilder {
        delegate.transformFeedbackVaryings(*names)
        return this
    }

    /**
     * 根据输入和 `ShaderProgramBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun build(): CooShaderProgram {
        return delegate.build()
    }

    /**
     * 在 `ShaderProgramBuilder` 中配置 `buildCompute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`buildCompute()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun buildCompute(): CooComputeShaderProgram {
        return delegate.buildCompute()
    }
}
