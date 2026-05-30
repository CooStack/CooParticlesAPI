package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import cn.coostack.cooparticlesapi.renderer.effects.builtin.BuiltinRenderEffectDescriptors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelExecutors
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelRenderer
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f
import org.joml.Matrix4fStack

/**
 * 客户端对单个 RenderEntity 的运行时包装。
 *
 * 它把“同步对象”和“渲染对象”拼接起来，负责：
 * - 初始化 renderer 生命周期
 * - 缓存 visual profile / feature set
 * - 执行 vanilla RenderType 路线和本地 OpenGL world pass 渲染
 * - 收集 frame-post contribution
 * - 在移除时释放本地资源
 */
class RenderEntityInstance<T : RenderEntity>(
    val entity: T,
    val renderer: RenderEntityRenderer<T>
) {
    /**
     * 当前实体的视觉画像快照。
     *
     * 由 renderer 根据实体状态重新计算，用于向管线声明颜色拷贝、深度依赖、混合模式等需求。
     */
    var visualProfile: RenderEntityVisualProfile = renderer.createVisualProfile(entity)
        private set
    /**
     * 当前实体声明的功能集。
     */
    var featureSet: RenderEntityFeatureSet = renderer.describeFeatures(entity)
        private set
    /**
     * 本地 world pass 渲染用的临时目标池。
     */
    val localRenderTargetPool = LocalRenderTargetPool()
    /**
     * world pass 后串行执行的本地 effect chain。
     */
    var localEffectChain = LocalEffectChain(localRenderTargetPool)
    private var initialized = false
    private var released = false
    private var renderTypeWorldPassSubmitted = false

    /**
     * 初始化 renderer 生命周期。
     *
     * 这个方法是幂等的；重复调用不会二次初始化。
     * 初始化完成后会重新拉取 visual profile 和 feature set，保证和 renderer 当前实现一致。
     */
    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true
        renderer.initialize(this)
        visualProfile = renderer.createVisualProfile(entity)
        featureSet = renderer.describeFeatures(entity)
    }

    /**
     * 强制重新初始化 runtime 包装。
     *
     * 适合 renderer 配置、资源或生命周期语义发生明显变化后重建状态。
     */
    fun reinitialize() {
        initialized = false
        initialize()
    }

    /**
     * 用网络同步过来的临时实体覆盖当前实例状态。
     *
     * 更新顺序是：
     * 1. 把同步字段回写到 `entity`
     * 2. 重新计算 visual profile / feature set
     * 3. 如果 renderer 显式声明了更新钩子，再通知其刷新本地缓存
     */
    fun updateFrom(profile: RenderEntity) {
        entity.loadProfileFromEntity(profile)
        visualProfile = renderer.createVisualProfile(entity)
        featureSet = renderer.describeFeatures(entity)
        @Suppress("UNCHECKED_CAST")
        val updateHook = renderer as? RenderEntityUpdateHook<T>
        updateHook?.update(this, entity)
    }

    /**
     * 执行当前实例的 world pass 渲染。
     *
     * 只有在 `localRendererEnabled == true`、声明了 `WORLD_PASS` 阶段，
     * 且 renderer 实现了 `WorldPassRenderEntityRenderer` 时才会进入真正绘制。
     */
    fun renderLocal(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4fStack,
        stateGuard: RenderStateGuard
    ) {
        if (consumeRenderTypeWorldPass()) {
            return
        }
        if (!featureSet.localRendererEnabled || RenderFrameStage.WORLD_PASS !in featureSet.stages) {
            return
        }
        stateGuard.use { renderState ->
            val localInput = LocalRenderInput(
                instance = this,
                tickDelta = tickDelta,
                viewMatrix = viewMatrix,
                projMatrix = projMatrix,
                modelMatrix = modelMatrix,
                renderState = renderState
            )
            @Suppress("UNCHECKED_CAST")
            val modelRenderer = renderer as? RenderEntityModelRenderer<T>
            if (modelRenderer != null) {
                RenderEntityModelExecutors.active().draw(
                    modelRenderer.buildModel(entity, tickDelta),
                    localInput
                )
            }
            @Suppress("UNCHECKED_CAST")
            val localRenderer = renderer as? WorldPassRenderEntityRenderer<T>
            localRenderer?.renderLocal(localInput)
            localEffectChain.execute()
        }
    }

    /**
     * 执行 vanilla `RenderType` / `MultiBufferSource` 路线。
     *
     * 这条路径只会调用实现了 [RenderTypeBackedRenderEntityRenderer] 的 renderer。
     * 它比本地 OpenGL world pass 更早提交，Iris 光影开启时可以被包装到 entity pass；
     * 没有 Iris 或 wrapper 不可用时，仍然按普通 Minecraft RenderType 绘制。
     */
    fun renderRenderType(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        irisShaderPackInUse: Boolean
    ) {
        if (RenderFrameStage.WORLD_PASS !in featureSet.stages) {
            return
        }
        @Suppress("UNCHECKED_CAST")
        val renderTypeRenderer = renderer as? RenderTypeBackedRenderEntityRenderer<T> ?: return
        if (!shouldRenderTypeWorldPass(renderTypeRenderer, irisShaderPackInUse)) {
            return
        }
        renderTypeRenderer.renderRenderType(
            RenderTypeRenderInput(
                instance = this,
                tickDelta = tickDelta,
                viewMatrix = viewMatrix,
                projMatrix = projMatrix,
                poseStack = poseStack,
                bufferSource = bufferSource,
                camera = camera
            )
        )
        renderTypeWorldPassSubmitted = true
    }

    /**
     * 开始新的 world render frame。由 vanilla buffer pass 调用，用来清理上一帧的路径状态。
     */
    fun beginWorldRenderFrame() {
        renderTypeWorldPassSubmitted = false
    }

    private fun shouldRenderTypeWorldPass(
        renderer: RenderTypeBackedRenderEntityRenderer<T>,
        irisShaderPackInUse: Boolean
    ): Boolean {
        return when (renderer.renderTypeMode(entity)) {
            RenderTypeBackedRenderMode.DISABLED -> false
            RenderTypeBackedRenderMode.ALWAYS_RENDER_TYPE,
            RenderTypeBackedRenderMode.DUAL -> true
            RenderTypeBackedRenderMode.IRIS_PROXY_WITH_OPENGL -> irisShaderPackInUse
            RenderTypeBackedRenderMode.IRIS_FIRST_OPENGL_FALLBACK -> {
                irisShaderPackInUse || !hasOpenGlWorldPass()
            }
        }
    }

    private fun consumeRenderTypeWorldPass(): Boolean {
        val submitted = renderTypeWorldPassSubmitted
        renderTypeWorldPassSubmitted = false
        if (!submitted) {
            return false
        }
        @Suppress("UNCHECKED_CAST")
        val renderTypeRenderer = renderer as? RenderTypeBackedRenderEntityRenderer<T> ?: return false
        return when (renderTypeRenderer.renderTypeMode(entity)) {
            RenderTypeBackedRenderMode.DUAL,
            RenderTypeBackedRenderMode.IRIS_PROXY_WITH_OPENGL,
            RenderTypeBackedRenderMode.DISABLED -> false
            RenderTypeBackedRenderMode.ALWAYS_RENDER_TYPE,
            RenderTypeBackedRenderMode.IRIS_FIRST_OPENGL_FALLBACK -> true
        }
    }

    private fun hasOpenGlWorldPass(): Boolean {
        return renderer is WorldPassRenderEntityRenderer<*> || renderer is RenderEntityModelRenderer<*>
    }

    /**
     * 仅在客户端运行时侧把实体标记为已移除。
     */
    fun markRemoved() {
        entity.canceled = true
    }

    /**
     * 收集当前实例在 frame-post 阶段提交的 descriptor。
     *
     * 只有在声明启用了 effect graph 且包含 `FRAME_POST` 阶段时才会进入。
     * 收集完成后，builtin effect provider 也会继续补充自己的贡献。
     */
    fun collectRenderContributions(context: RenderFrameContext, collector: RenderContributionCollector) {
        if (!featureSet.effectGraphEnabled || RenderFrameStage.FRAME_POST !in featureSet.stages) {
            return
        }
        @Suppress("UNCHECKED_CAST")
        val framePostRenderer = renderer as? FramePostRenderEntityRenderer<T>
        framePostRenderer?.collectRenderContributions(
            RenderContributionInput(
                instance = this,
                frameContext = context
            ),
            collector
        )
        BuiltinRenderEffectDescriptors.collectEntity(entity, context, collector)
    }

    /**
     * 释放 runtime 包装持有的本地资源。
     *
     * 该方法也是幂等的；一旦释放过，就不会重复执行 release 逻辑。
     */
    fun release() {
        if (released) {
            return
        }
        released = true
        localEffectChain.release()
        @Suppress("UNCHECKED_CAST")
        val releaseHook = renderer as? RenderEntityReleaseHook<T>
        releaseHook?.release(this)
    }
}
