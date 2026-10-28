package cn.coostack.cooparticlesapi.compat.iris

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.compat.IrisOpenGlProgramBridge
import cn.coostack.cooparticlesapi.compat.IrisShadowPassState
import cn.coostack.cooparticlesapi.display.CooRenderTypePipelineFactory
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.renderpearl.frontend.FrontendRenderPipeline
import com.mojang.renderpearl.api.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import java.util.WeakHashMap
import java.util.function.Consumer
import org.lwjgl.opengl.GL33

/**
 * 为必须保留自有片元的原版 collector 管线选择 Iris 目标，不替换 shader 或搬迁暂存网格。
 *
 * 仅工厂登记的管线进入该路径。原版 draw 仍负责本次 DynamicTransforms、纹理、uniform、
 * 顶点和索引绑定；Iris 公开 program 状态只用于当前目标与阶段，回调结束后完整恢复。
 */
object CooIrisNativePipelineDraw {
    /** RenderPipeline 沿用对象身份 equals，弱键使外部仍缓存的 RenderType 在重载后保留兼容路由。 */
    private val pipelines = WeakHashMap<RenderPipeline, String>()
    /** Iris 会扩展 collector 顶点布局；原程序变体必须明确使用相同的实际 stride。 */
    private val nativePipelines = WeakHashMap<RenderPipeline, MutableMap<List<VertexFormat?>, RenderPipeline>>()
    /** 已报告缺失目标的 key，目标恢复后移除以允许下次独立故障再次报告。 */
    private val warnedTargets = mutableSetOf<String>()

    /**
     * 登记需要保留原片元的管线，不操作 GL 或直接加载 Iris 类型。
     * 示例：`register(pipeline, "ENTITIES_EYES")`。
     * @param pipeline 本周期原版拥有的独立管线
     * @param shaderKey 用于选择 Iris framebuffer 的精确 ShaderKey 名称
     */
    fun register(pipeline: RenderPipeline, shaderKey: String) {
        synchronized(pipelines) { pipelines[pipeline] = shaderKey }
    }

    /**
     * 清除派生布局缓存；外部仍持有的管线路由通过弱键保留，不释放原版或 Iris 资源。
     * 示例：资源重载时调用 `clear()`。
     */
    fun clear() {
        nativePipelines.clear()
        warnedTargets.clear()
    }

    /**
     * 在 Iris 真实目标中同步执行原版 draw；无光影或未登记时不接管。
     * 示例：`draw(pipeline, renderPass) { variant -> original.draw(variant) }`。
     * @param pipeline PreparedRenderType 为本次绘制选择的管线
     * @param renderPass 当前原版仍打开的 pass，用于强制刷新桥接后可能被缓存跳过的顶点绑定
     * @param originalDraw 不得跨调用保留的原版 draw 回调
     * @return 已执行或已显式处理该原版绘制时返回 true
     */
    @JvmStatic
    fun draw(pipeline: RenderPipeline, renderPass: RenderPass, originalDraw: Consumer<RenderPipeline>): Boolean {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return false
        val route = synchronized(pipelines) { pipelines[pipeline] } ?: return false
        val shadow = IrisCompat.shadowPassState() == IrisShadowPassState.ACTIVE
        val key = if (shadow) {
            if (route == "BASIC_COLOR") "SHADOW_BASIC_COLOR" else "SHADOW_ENTITIES_CUTOUT"
        } else route
        val bindingPipeline = when (route) {
            "BASIC_COLOR" -> RenderPipelines.DEBUG_FILLED_BOX
            "ENTITIES_EYES", "ENTITIES_EYES_TRANS" -> RenderPipelines.EYES
            "ENTITIES_TRANSLUCENT" -> RenderPipelines.ENTITY_TRANSLUCENT
            else -> RenderPipelines.ENTITY_CUTOUT
        }
        val camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        val immediate = Class.forName("net.irisshaders.iris.vertices.ImmediateState")
        val bypass = immediate.getField("bypass")
        val previousBypass = bypass.getBoolean(null)
        val formats = pipeline.vertexFormatBindings.toList()
        val nativePipeline = nativePipelines.getOrPut(pipeline) { linkedMapOf() }.getOrPut(formats) {
            CooRenderTypePipelineFactory.copyBuilder(pipeline).apply {
                formats.forEachIndexed { index, format ->
                    if (format != null) withVertexBinding(index, format)
                }
            }.build()
        }
        var isolatePackOutputs = true
        val nativeDraw = {
            bypass.setBoolean(null, true)
            try {
                // CommandEncoder 可以按管线身份省略 bind；桥接临时使用 Iris 程序后必须显式重绑。
                val compiled = RenderSystem.getCompiledPipeline(nativePipeline) as FrontendRenderPipeline
                (compiled.backendRenderPipeline() as GlRenderPipeline).bind()
                // 自定义程序只声明的输出才允许写入，不能让未定义输出污染光影包的辅助附件。
                if (isolatePackOutputs) {
                    for (index in nativePipeline.colorTargetStates.size until GL33.glGetInteger(GL33.GL_MAX_DRAW_BUFFERS)) {
                        if (index < ColorTargetState.MAX_COLOR_TARGETS) GlStateManager._colorMask(index, 0)
                        GL33.glColorMaski(index, false, false, false, false)
                    }
                }
                // 原版按 buffer 与 offset 去重；先清空该槽可保证恢复后的 VAO 接收本次原始绑定。
                renderPass.setVertexBuffer(0, null)
                originalDraw.accept(nativePipeline)
            } finally {
                bypass.setBoolean(null, previousBypass)
            }
        }
        if (nativePipeline.colorTargetStates.size != 1) {
            val unsupportedTarget = "$route/multiple-outputs"
            if (warnedTargets.add(unsupportedTarget)) {
                CooParticlesConstants.logger.warn(
                    "Custom collector pipeline {} declares multiple color outputs; preserving its caller-owned pass instead of assigning shader-pack attachments",
                    pipeline.location
                )
            }
            isolatePackOutputs = false
            nativeDraw()
            return true
        }
        val drawn = IrisOpenGlProgramBridge.draw(
            key, bindingPipeline, camera.viewRotationMatrix, camera.projectionMatrix,
            fallback = { nativeDraw() }, block = { nativeDraw() }
        )
        if (!drawn) {
            if (warnedTargets.add(key)) {
                CooParticlesConstants.logger.error(
                    "Iris {} target is unavailable; preserving the custom collector draw in its caller-owned target", key
                )
            }
            isolatePackOutputs = false
            nativeDraw()
        } else warnedTargets.remove(key)
        return true
    }
}
