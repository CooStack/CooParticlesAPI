package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge
import cn.coostack.cooparticlesapi.renderer.state.CooGLSLStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.backend.opengl.GlBuffer
import com.mojang.renderpearl.backend.opengl.GlProgram
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.renderpearl.backend.opengl.Uniform
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import org.joml.Matrix4f
import org.lwjgl.opengl.ARBShaderImageLoadStore
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GL42
import java.util.Optional

/**
 * 将既有直接 OpenGL 几何绘制接入 26.3 Iris 的公开 program 与绑定布局。
 *
 * 只在渲染线程、OpenGL 后端和已启用的光影包中使用；不拥有 Iris program 或纹理。
 * 原版 collector 路径不经过此桥接。只有真实执行回调才报告提交成功，不能拿错误 program 继续绘制。
 */
internal object IrisOpenGlProgramBridge {
    /** 已报告的不支持入口，避免同一缺失的 shader 每帧刷屏。 */
    private val failedPrograms = mutableSetOf<String>()

    /**
     * 绑定 Iris shader、原版 UBO 和其 framebuffer，执行绘制后恢复入口状态。
     *
     * 示例：粒子批次传入 `PARTICLES_TRANS` 和原版半透明粒子管线。
     * @param key Iris 公开 ShaderKey 的成员名称
     * @param pipeline 提供原版绑定布局的对应管线
     * @param view 当前批次的模型视图矩阵
     * @param projection 当前世界投影矩阵
     * @param fallback tessellation program 无法接收普通三角形时，在同一 Iris 目标中执行的本库 shader 绘制
     * @param block 不得保留绑定资源或在返回后继续绘制的同步回调
     * @return 原生或保留几何的回退回调确实执行后返回 true；无法取得目标时返回 false
     */
    fun draw(
        key: String, pipeline: RenderPipeline, view: Matrix4f, projection: Matrix4f,
        fallback: (() -> Unit)? = null, block: () -> Unit,
    ): Boolean {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            block()
            return true
        }
        OpenGlRenderTargetBridge.requireOpenGlBackend()
        val resolved = runCatching { resolve(key) }.getOrElse { failure ->
            if (failedPrograms.add(key)) {
                CooParticlesConstants.logger.error("无法解析 26.3 Iris program {}，调用方必须保留后续绘制机会", key, failure)
            }
            return false
        } ?: run {
            if (failedPrograms.add(key)) {
                CooParticlesConstants.logger.error("当前 Iris 管线未提供 {}，调用方必须保留后续绘制机会", key)
            }
            return false
        }
        failedPrograms.remove(key)
        val program = resolved.first
        val irisProgram = resolved.second
        val extendedType = Class.forName("net.irisshaders.iris.pipeline.programs.ExtendedShader")
        val extended = extendedType.isInstance(program)
        val tessellation = extended && extendedType.getMethod("usesTessellation").invoke(program) == true
        val images = extended && extendedType.getMethod("hasActiveImages").invoke(program) == true
        val capabilities = GL.getCapabilities()
        val route = IrisProgramDrawPolicy.route(
            usesTessellation = tessellation,
            hasActiveImages = images,
            imageLoadStoreSupported = capabilities.OpenGL42 || capabilities.GL_ARB_shader_image_load_store,
            fallbackAvailable = fallback != null,
        )
        if (route == IrisProgramDrawRoute.UNAVAILABLE) {
            if (failedPrograms.add("$key/capability")) {
                CooParticlesConstants.logger.error(
                    "Iris {} 缺少 image load/store 能力或 patch 拓扑回退，未提交且保留入口 GL 状态", key,
                )
            }
            return false
        }
        val imageState = if (images) captureImages() else emptyList()
        val uniforms = BindGroupLayout.flattenUniforms(pipeline.bindGroupLayouts)
        val bindings = Class.forName("net.irisshaders.iris.mixinterface.GlProgramBindings")
        val setupBindings = bindings.getMethod("iris\$setupBindings", List::class.java, Int::class.javaPrimitiveType)
        val setupState = irisProgram.getMethod("iris\$setupState", List::class.java)
        val clearState = irisProgram.getMethod("iris\$clearState")
        val originalProgram = GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM)
        val originalDrawFbo = GL33.glGetInteger(GL33.GL_DRAW_FRAMEBUFFER_BINDING)
        val originalReadFbo = GL33.glGetInteger(GL33.GL_READ_FRAMEBUFFER_BINDING)
        val originalVao = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val originalUniformBuffer = GL33.glGetInteger(GL33.GL_UNIFORM_BUFFER_BINDING)
        val viewport = IntArray(4)
        GL33.glGetIntegerv(GL33.GL_VIEWPORT, viewport)
        val textureState = captureTextures()
        val uniformState = mutableListOf<LongArray>()
        val immediateState = Class.forName("net.irisshaders.iris.vertices.ImmediateState")
        val tessellationField = immediateState.getField("usingTessellation")
        val previousTessellation = tessellationField.getBoolean(null)
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.set(view)
        var submitted = false
        try {
            CooGLSLStateManager.useState {
                ProjectionMatrixBuffer("CooParticlesAPI Iris projection").use { projectionBuffer ->
                    setupBindings.invoke(program, uniforms, pipeline.pushConstantSize())
                    uniforms.indices.forEach { index ->
                        val uniform = program.getUniform(index) as? Uniform.Ubo ?: return@forEach
                        val binding = uniform.blockBinding()
                        uniformState += longArrayOf(
                            binding.toLong(),
                            GL33.glGetIntegeri(GL33.GL_UNIFORM_BUFFER_BINDING, binding).toLong(),
                            GL33.glGetInteger64i(GL33.GL_UNIFORM_BUFFER_START, binding),
                            GL33.glGetInteger64i(GL33.GL_UNIFORM_BUFFER_SIZE, binding),
                        )
                    }
                    try {
                        setupState.invoke(program, uniforms)
                        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(view)
                        val projectionSlice = projectionBuffer.getBuffer(projection)
                        uniforms.forEachIndexed { index, description ->
                            val uniform = program.getUniform(index) as? Uniform.Ubo ?: return@forEachIndexed
                            val binding = uniform.blockBinding()
                            val slice = when (description.name()) {
                                "DynamicTransforms" -> dynamicTransforms
                                "Projection" -> projectionSlice
                                "Fog" -> RenderSystem.getShaderFog()
                                "Globals" -> RenderSystem.getGlobalSettingsUniform()?.slice()
                                "Lighting" -> RenderSystem.getShaderLights()
                                else -> error("Iris 原版管线包含未接入的 UBO：${description.name()}")
                            }
                            bindUniform(binding, requireNotNull(slice) { "Iris 绘制缺少 ${description.name()} UBO" })
                        }
                        if (route == IrisProgramDrawRoute.OWN_SHADER) {
                            tessellationField.setBoolean(null, false)
                            // 回退 shader 只定义主颜色；禁止未定义输出改写光影包辅助 attachment。
                            repeat(GL33.glGetInteger(GL33.GL_MAX_DRAW_BUFFERS)) { drawBuffer ->
                                if (drawBuffer != 0) GL33.glColorMaski(drawBuffer, false, false, false, false)
                            }
                        }
                        submitted = IrisProgramDrawPolicy.submit(route, fallback, block)
                    } finally {
                        try {
                            if (images) publishImageWrites()
                        } finally {
                            clearState.invoke(program)
                        }
                    }
                }
            }
        } finally {
            modelView.popMatrix()
            tessellationField.setBoolean(null, previousTessellation)
            restoreImages(imageState)
            uniformState.asReversed().forEach { state ->
                val binding = state[0].toInt()
                if (state[1] == 0L || state[3] == 0L) {
                    GL33.glBindBufferBase(GL33.GL_UNIFORM_BUFFER, binding, state[1].toInt())
                } else {
                    GL33.glBindBufferRange(GL33.GL_UNIFORM_BUFFER, binding, state[1].toInt(), state[2], state[3])
                }
            }
            GlStateManager._glBindBuffer(GL33.GL_UNIFORM_BUFFER, originalUniformBuffer)
            restoreTextures(textureState)
            GlStateManager._glUseProgram(originalProgram)
            GL33.glBindVertexArray(originalVao)
            OpenGlRenderTargetBridge.bindFramebuffer(GL33.GL_DRAW_FRAMEBUFFER, originalDrawFbo)
            OpenGlRenderTargetBridge.bindFramebuffer(GL33.GL_READ_FRAMEBUFFER, originalReadFbo)
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        }
        return submitted
    }

    /** 仅反射 Iris 的公开类型，不把可选类型放入公共签名或服务端初始化路径。 */
    private fun resolve(key: String): Pair<GlProgram, Class<*>>? {
        val iris = Class.forName("net.irisshaders.iris.Iris")
        val manager = iris.getMethod("getPipelineManager").invoke(null)
        val managerType = Class.forName("net.irisshaders.iris.pipeline.PipelineManager")
        val pipeline = (managerType.getMethod("getPipeline").invoke(manager) as Optional<*>).orElse(null) ?: return null
        val shaderPipeline = Class.forName("net.irisshaders.iris.pipeline.ShaderRenderingPipeline")
        if (!shaderPipeline.isInstance(pipeline)) return null
        val shaderMap = shaderPipeline.getMethod("getShaderMap").invoke(pipeline)
        val keyType = Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey")
        val shaderKey = keyType.getField(key).get(null)
        val mapType = Class.forName("net.irisshaders.iris.pipeline.programs.ShaderMap")
        val program = mapType.getMethod("getShader", keyType).invoke(shaderMap, shaderKey) as? GlProgram ?: return null
        val programType = Class.forName("net.irisshaders.iris.pipeline.programs.IrisProgram")
        require(programType.isInstance(program)) { "Iris ShaderMap 返回了不支持状态绑定的 program" }
        return program to programType
    }

    /** 在 Iris 公开 setupState 更新 image 前保存全部单元，不假设光影包只使用连续的低编号。 */
    private fun captureImages(): List<IntArray> {
        val capabilities = GL.getCapabilities()
        check(capabilities.OpenGL42 || capabilities.GL_ARB_shader_image_load_store) {
            "Iris image program 需要 OpenGL 4.2 或 ARB_shader_image_load_store"
        }
        return List(GL33.glGetInteger(GL42.GL_MAX_IMAGE_UNITS)) { unit ->
            intArrayOf(
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME, unit),
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL, unit),
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED, unit),
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER, unit),
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS, unit),
                GL33.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT, unit),
            )
        }
    }

    /** 发布本次 shader 的 image 写入，后续纹理采样、image 访问与 framebuffer 操作均可观察结果。 */
    private fun publishImageWrites() {
        if (GL.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        } else {
            ARBShaderImageLoadStore.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
        }
    }

    /** 完整恢复 image 的层级、访问模式和格式，避免只恢复纹理编号后改变周围光影包语义。 */
    private fun restoreImages(snapshot: List<IntArray>) {
        snapshot.forEachIndexed { unit, state ->
            if (GL.getCapabilities().OpenGL42) {
                GL42.glBindImageTexture(unit, state[0], state[1], state[2] != 0, state[3], state[4], state[5])
            } else {
                ARBShaderImageLoadStore.glBindImageTexture(
                    unit, state[0], state[1], state[2] != 0, state[3], state[4], state[5],
                )
            }
        }
    }

    /** 按实际 OpenGL buffer 句柄绑定原版切片；该函数不接管切片资源的释放。 */
    private fun bindUniform(binding: Int, slice: GpuBufferSlice) {
        val buffer = slice.buffer() as? GlBuffer ?: error("Iris 直接绘制不能绑定非 OpenGL buffer")
        GL33.glBindBufferRange(GL33.GL_UNIFORM_BUFFER, binding, buffer.handle(), slice.offset(), slice.length())
    }

    /** Iris 可以绑定辅助采样器，因此不能只保护调用方使用的前三个纹理单元。 */
    private fun captureTextures(): Pair<Int, List<IntArray>> {
        val active = GL33.glGetInteger(GL33.GL_ACTIVE_TEXTURE)
        val states = List(GL33.glGetInteger(GL33.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) { unit ->
            GlStateManager._activeTexture(GL33.GL_TEXTURE0 + unit)
            GL33.glActiveTexture(GL33.GL_TEXTURE0 + unit)
            intArrayOf(
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_1D),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_3D),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_CUBE_MAP),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_1D_ARRAY),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D_ARRAY),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_RECTANGLE),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_BUFFER),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D_MULTISAMPLE),
                GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY),
                GL33.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit),
            )
        }
        GlStateManager._activeTexture(active)
        GL33.glActiveTexture(active)
        return active to states
    }

    /** 恢复纹理时同步原版维护的十二个二维绑定缓存，其余单元不在该缓存中。 */
    private fun restoreTextures(snapshot: Pair<Int, List<IntArray>>) {
        snapshot.second.forEachIndexed { unit, state ->
            GlStateManager._activeTexture(GL33.GL_TEXTURE0 + unit)
            GL33.glActiveTexture(GL33.GL_TEXTURE0 + unit)
            GL33.glBindTexture(GL33.GL_TEXTURE_1D, state[0])
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, state[1])
            if (unit < 12) GlStateManager._bindTexture(state[1])
            GL33.glBindTexture(GL33.GL_TEXTURE_3D, state[2])
            GL33.glBindTexture(GL33.GL_TEXTURE_CUBE_MAP, state[3])
            GL33.glBindTexture(GL33.GL_TEXTURE_1D_ARRAY, state[4])
            GL33.glBindTexture(GL33.GL_TEXTURE_2D_ARRAY, state[5])
            GL33.glBindTexture(GL33.GL_TEXTURE_RECTANGLE, state[6])
            GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, state[7])
            GL33.glBindTexture(GL33.GL_TEXTURE_2D_MULTISAMPLE, state[8])
            GL33.glBindTexture(GL33.GL_TEXTURE_2D_MULTISAMPLE_ARRAY, state[9])
            GL33.glBindSampler(unit, state[10])
        }
        GlStateManager._activeTexture(snapshot.first)
        GL33.glActiveTexture(snapshot.first)
    }
}
