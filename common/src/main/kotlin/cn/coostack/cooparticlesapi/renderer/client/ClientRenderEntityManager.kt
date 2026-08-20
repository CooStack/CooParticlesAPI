package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectGraph
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityPipelineRuntimeCache
import cn.coostack.cooparticlesapi.renderer.state.CooGLSLStateManager
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import net.minecraft.client.Minecraft
import net.minecraft.client.Minecraft.getInstance
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import java.util.UUID
import kotlin.collections.iterator

object ClientRenderEntityManager {
    val minecraft: Minecraft = getInstance()
    private val entities = HashMap<UUID, RenderEntityInstance<RenderEntity>>()
    private var frameStatePrepared = false
    private var cachedTickDelta = 0F
    private val cachedViewMatrix = Matrix4f()
    private val cachedProjMatrix = Matrix4f()
    private val renderStateGuard = RenderStateGuard()
    /**
     * 从 `ClientRenderEntityManager` 当前维护的状态中读取 `getFrom` 结果，不创建新的渲染资源。
     *
     * 示例：`getFrom(uuid = uuid)`。
     *
     * @param uuid 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun getFrom(uuid: UUID): RenderEntityInstance<RenderEntity>? {
        return entities[uuid]
    }

    /**
     * 返回客户端当前持有的 RenderEntity 实例数量。
     *
     * 示例：F3 调试信息可用 `loadedEntityCount()` 显示当前加载量。
     * 禁止把该值理解为已注册的 RenderEntity 类型数或服务端实例数。
     *
     * @return 当前客户端运行时实例数量
     */
    @JvmStatic
    fun loadedEntityCount(): Int = entities.size

    /**
     * 清理 `ClientRenderEntityManager` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear()`。
     */
    fun clear() {
        entities.clear()
        frameStatePrepared = false
        cachedTickDelta = 0F
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        CooPostEffects.client.clear()
    }

    /** 失效渲染缓存，并让当前实体按共享 pipeline 重新初始化。 */
    fun onShaderReload() {
        frameStatePrepared = false
        cachedTickDelta = 0F
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        RenderEntityPipelineRuntimeCache.invalidate()
        entities.values.forEach { instance ->
            instance.reinitialize()
        }
    }

    /**
     * 把输入对象加入 `ClientRenderEntityManager` 的 `add` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`add(instance = instance)`。
     *
     * @param instance 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun add(instance: RenderEntityInstance<RenderEntity>) {
        instance.entity.world = minecraft.level
        instance.entity.lastRenderPos = instance.entity.pos
        entities[instance.entity.uuid] = instance
    }

    /**
     * 初始化或准备 `ClientRenderEntityManager` 的 `beginWorldRenderFrame` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`beginWorldRenderFrame()`。
     */
    fun beginWorldRenderFrame() {
        CooGLSLStateManager.assertStateStackEmpty()
        entities.values.forEach(RenderEntityInstance<RenderEntity>::beginWorldRenderFrame)
    }

    /**
     * 执行 `ClientRenderEntityManager` 的 `renderWorldPass` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`renderWorldPass(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     */
    fun renderWorldPass(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderWorldPass(tickDelta, viewMatrix, projMatrix, scenePost = false)
    }

    /** 在场景 composite 前提交属于场景后处理 Pipeline 的世界几何。 */
    fun renderSceneWorldPass(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderWorldPass(tickDelta, viewMatrix, projMatrix, scenePost = true)
    }

    private fun renderWorldPass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        scenePost: Boolean
    ) {
        val stack = Matrix4fStack(16)
        entities.values.asSequence()
            .filter { instance -> instance.usesScenePost() == scenePost }
            .forEach { instance ->
                val entity = instance.entity
                stack.pushMatrix()
                RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
                instance.render(tickDelta, viewMatrix, projMatrix, stack, renderStateGuard)
                stack.popMatrix()
                entity.lastRenderPos = entity.pos
            }
    }

    /**
     * 执行 `ClientRenderEntityManager` 的 `renderIrisWorldPass` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`renderIrisWorldPass(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix, irisShaderPackInUse = irisShaderPackInUse)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     *
     * @param irisShaderPackInUse 控制是否启用对应分支或强制执行操作的开关
     */
    fun renderIrisWorldPass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        irisShaderPackInUse: Boolean
    ) {
        // screen-only Terrain Mapping 需要先合成最终光影画面；此时 RenderEntity 延后到 API world/scene pass。
        if (!irisShaderPackInUse || CooTerrainPipelineManager.shouldDeferShaderPackRenderEntities()) {
            return
        }
        val stack = Matrix4fStack(16)
        entities.values.asSequence()
            .filter(RenderEntityInstance<RenderEntity>::isShaderPackHandled)
            .forEach { instance ->
                val entity = instance.entity
                stack.pushMatrix()
                RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
                instance.renderIrisWorldPass(tickDelta, viewMatrix, projMatrix, stack, renderStateGuard)
                stack.popMatrix()
                // 可见 Iris pass 已经使用了本帧插值位置；后续 offscreen 捕获应沿用缓存矩阵而不是重复插值。
                entity.lastRenderPos = entity.pos
            }
    }

    /**
     * 执行 `ClientRenderEntityManager` 定义的 `cacheFrameState` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`cacheFrameState(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     */
    fun cacheFrameState(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        cachedTickDelta = tickDelta
        cachedViewMatrix.set(viewMatrix)
        cachedProjMatrix.set(projMatrix)
        frameStatePrepared = true
    }

    /**
     * 初始化或准备 `ClientRenderEntityManager` 的 `preparePostProcess` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`preparePostProcess(tickDelta = tickDelta, viewMatrix = viewMatrix, projMatrix = projMatrix)`。
     *
     * @param tickDelta 当前 tick 内的插值比例，通常位于 0 到 1
     *
     * @param viewMatrix 把世界坐标变换到相机空间的视图矩阵
     *
     * @param projMatrix 把相机空间坐标变换到裁剪空间的投影矩阵
     */
    fun preparePostProcess(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        if (!frameStatePrepared) {
            cacheFrameState(tickDelta, viewMatrix, projMatrix)
        }
    }

    /**
     * 执行 `ClientRenderEntityManager` 的 `flushFrameComposites` 渲染操作，处理传入数据并更新当前帧或 GPU 状态。
     *
     * 示例：`flushFrameComposites()`。
     */
    fun flushFrameComposites() {
        frameStatePrepared = false
        cachedTickDelta = 0F
    }

    /**
     * 执行 `ClientRenderEntityManager` 定义的 `runFramePost` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`runFramePost(context = context)`。
     *
     * @param context 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun runFramePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST)) {
            return
        }
        val graph = RenderEffectGraph(context.backend.capabilities, context)
        entities.values.asSequence()
            .filterNot(RenderEntityInstance<RenderEntity>::usesScenePost)
            .forEach { instance ->
                instance.collectEffects(context, graph)
            }
        CooTerrainPipelineManager.collectPostEffects(context, graph)
        CooPostEffects.client.collectFramePost(context, graph)
        graph.execute()
    }

    /** 执行需要参与后续云层或 shader pack composite 的 Pipeline fullscreen 节点。 */
    fun runScenePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST)) {
            return
        }
        // Terrain Mapping 先提交，避免大范围映射覆盖 RenderEntity 的场景后处理结果。
        val terrainGraph = RenderEffectGraph(context.backend.capabilities, context)
        CooTerrainPipelineManager.collectScenePostEffects(context, terrainGraph)
        terrainGraph.execute()

        val graph = RenderEffectGraph(context.backend.capabilities, context)
        entities.values.asSequence()
            .filter(RenderEntityInstance<RenderEntity>::usesScenePost)
            .forEach { instance -> instance.collectEffects(context, graph) }
        graph.execute()
    }

    /** 在 Iris final pass 前只捕获场景后处理 Pipeline 的 world attachment。 */
    fun captureScenePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST) ||
            CooTerrainPipelineManager.shouldDeferShaderPackRenderEntities()
        ) {
            return
        }
        val graph = RenderEffectGraph(context.backend.capabilities, context)
        entities.values.asSequence()
            .filter(RenderEntityInstance<RenderEntity>::usesScenePost)
            .filter(RenderEntityInstance<RenderEntity>::isShaderPackHandled)
            .forEach { instance -> instance.collectPipelineEffect(context, graph) }
        graph.execute()
    }

    /**
     * 更新 `ClientRenderEntityManager` 的 `tick` 状态；修改会影响后续查询、构建或当前帧绘制。
     *
     * 示例：`tick()`。
     */
    fun tick() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val instance = entry.value
            val entity = instance.entity
            entity.tick()
            if (entity.canceled) {
                iterator.remove()
            }
        }
        CooPostEffects.client.tick()
    }

}
