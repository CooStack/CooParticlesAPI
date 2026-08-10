package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectGraph
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityPipelineRuntimeCache
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

    fun add(instance: RenderEntityInstance<RenderEntity>) {
        instance.entity.world = minecraft.level
        instance.entity.lastRenderPos = instance.entity.pos
        entities[instance.entity.uuid] = instance
    }

    fun beginWorldRenderFrame() {
        entities.values.forEach(RenderEntityInstance<RenderEntity>::beginWorldRenderFrame)
    }

    fun renderWorldPass(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        val stack = Matrix4fStack(16)
        entities.values.forEach { instance ->
            val entity = instance.entity
            stack.pushMatrix()
            RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
            instance.render(tickDelta, viewMatrix, projMatrix, stack, renderStateGuard)
            stack.popMatrix()
            entity.lastRenderPos = entity.pos
        }
    }

    fun renderIrisWorldPass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        irisShaderPackInUse: Boolean
    ) {
        if (!irisShaderPackInUse) {
            return
        }
        val stack = Matrix4fStack(16)
        entities.values.forEach { instance ->
            val entity = instance.entity
            stack.pushMatrix()
            RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
            instance.renderIrisWorldPass(tickDelta, viewMatrix, projMatrix, stack, renderStateGuard)
            stack.popMatrix()
        }
    }

    fun cacheFrameState(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        cachedTickDelta = tickDelta
        cachedViewMatrix.set(viewMatrix)
        cachedProjMatrix.set(projMatrix)
        frameStatePrepared = true
    }

    fun preparePostProcess(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        if (!frameStatePrepared) {
            cacheFrameState(tickDelta, viewMatrix, projMatrix)
        }
    }

    fun flushFrameComposites() {
        frameStatePrepared = false
        cachedTickDelta = 0F
    }

    fun runFramePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST)) {
            return
        }
        val graph = RenderEffectGraph(context.backend.capabilities, context)
        entities.values.forEach { instance ->
            instance.collectEffects(context, graph)
        }
        CooTerrainPipelineManager.collectPostEffects(context, graph)
        CooPostEffects.client.collectFramePost(context, graph)
        graph.execute()
    }

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
