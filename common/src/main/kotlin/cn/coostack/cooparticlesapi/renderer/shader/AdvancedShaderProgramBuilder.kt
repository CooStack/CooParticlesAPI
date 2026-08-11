package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.CooShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import net.minecraft.resources.ResourceLocation

class AdvancedShaderProgramBuilder {
    private var vertex: GlShader? = null
    private var fragment: GlShader? = null
    private var geometry: GlShader? = null
    private var tessellationControl: GlShader? = null
    private var tessellationEvaluation: GlShader? = null
    private var compute: GlShader? = null
    private val shaderBufferLayouts = mutableListOf<ShaderBufferLayout<*>>()
    private val attributeLocations = linkedMapOf<String, Int>()
    private val transformFeedbackVaryings = mutableListOf<String>()
    private var managedProgramId: ResourceLocation? = null

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `vertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vertex(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vertex(path: String): AdvancedShaderProgramBuilder {
        vertex = identifier(path, GlShaderType.VERTEX)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun fragment(path: String): AdvancedShaderProgramBuilder {
        fragment = identifier(path, GlShaderType.FRAGMENT)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `geometry`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`geometry(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun geometry(path: String): AdvancedShaderProgramBuilder {
        geometry = identifier(path, GlShaderType.GEOMETRY)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `tessellationControl`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationControl(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationControl(path: String): AdvancedShaderProgramBuilder {
        tessellationControl = identifier(path, GlShaderType.TESSELLATION_CONTROL)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `tessellationEvaluation`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationEvaluation(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationEvaluation(path: String): AdvancedShaderProgramBuilder {
        tessellationEvaluation = identifier(path, GlShaderType.TESSELLATION_EVALUATION)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `compute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`compute(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun compute(path: String): AdvancedShaderProgramBuilder {
        compute = identifier(path, GlShaderType.COMPUTE)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `vertex`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`vertex(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun vertex(shader: GlShader): AdvancedShaderProgramBuilder {
        vertex = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `fragment`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`fragment(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun fragment(shader: GlShader): AdvancedShaderProgramBuilder {
        fragment = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `geometry`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`geometry(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun geometry(shader: GlShader): AdvancedShaderProgramBuilder {
        geometry = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `tessellationControl`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationControl(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationControl(shader: GlShader): AdvancedShaderProgramBuilder {
        tessellationControl = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `tessellationEvaluation`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`tessellationEvaluation(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun tessellationEvaluation(shader: GlShader): AdvancedShaderProgramBuilder {
        tessellationEvaluation = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `compute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`compute(shader = shader)`。
     *
     * @param shader 要加载、编译或绑定的 shader 资源
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun compute(shader: GlShader): AdvancedShaderProgramBuilder {
        compute = shader
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `managedId`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`managedId(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun managedId(id: ResourceLocation): AdvancedShaderProgramBuilder {
        managedProgramId = id
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `managedId`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`managedId(path = path)`。
     *
     * @param path 用于查找、绑定或记录目标的名称
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun managedId(path: String): AdvancedShaderProgramBuilder {
        managedProgramId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `bufferLayout`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`bufferLayout(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun bufferLayout(layout: ShaderBufferLayout<*>): AdvancedShaderProgramBuilder {
        shaderBufferLayouts += layout
        return this
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `attributeLocation`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`attributeLocation(name = name, location = location)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @param location 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun attributeLocation(name: String, location: Int): AdvancedShaderProgramBuilder {
        require(location >= 0) { "attribute location must be non-negative" }
        attributeLocations[name] = location
        return this
    }

    /**
     * 在链接前声明交错存储的 transform-feedback 输出。
     *
     * 示例：`transformFeedbackVaryings("position", "uv")` 按声明顺序写入同一缓冲。
     * 禁止在 shader 中不存在对应输出时调用，否则 program 链接会失败。
     *
     * @param names vertex/geometry shader 中的输出变量名
     * @return 当前 builder
     */
    fun transformFeedbackVaryings(vararg names: String): AdvancedShaderProgramBuilder {
        require(names.all { it.isNotBlank() }) { "transform feedback varying name must not be blank" }
        transformFeedbackVaryings += names
        return this
    }

    /**
     * 根据输入和 `AdvancedShaderProgramBuilder` 当前配置创建 `build` 结果；返回对象保留本次配置的语义。
     *
     * 示例：`build()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun build(): CooShaderProgram {
        require(compute == null) { "compute shader must be built with buildCompute()" }
        check(vertex != null && fragment != null) { "vertex and fragment can not be null" }
        return ShaderProgramRegistry.register(
            SimpleShaderProgram(
            vertexShader = vertex!!,
            fragmentShader = fragment!!,
            geometryShaderInternal = geometry,
            tessellationControlShaderInternal = tessellationControl,
            tessellationEvaluationShaderInternal = tessellationEvaluation,
            shaderBufferLayoutsInternal = shaderBufferLayouts.toList(),
            attributeLocationsInternal = attributeLocations.toMap(),
            transformFeedbackVaryingsInternal = transformFeedbackVaryings.toList(),
            managedProgramIdInternal = managedProgramId
            )
        )
    }

    /**
     * 在 `AdvancedShaderProgramBuilder` 中配置 `buildCompute`；该调用只更新待构建数据，不会单独提交 GPU 绘制。
     *
     * 示例：`buildCompute()`。
     *
     * @return 当前构建器或由其配置生成的结果
     */
    fun buildCompute(): CooComputeShaderProgram {
        require(vertex == null && fragment == null && geometry == null && tessellationControl == null && tessellationEvaluation == null) {
            "compute shader program can not be mixed with graphics shader stages"
        }
        check(compute != null) { "compute shader can not be null" }
        return ShaderProgramRegistry.register(
            ComputeShaderProgram(compute!!, shaderBufferLayouts.toList(), managedProgramId)
        )
    }

    private fun identifier(path: String, type: GlShaderType): GlShader {
        return IdentifierShader(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path),
            type
        )
    }
}
