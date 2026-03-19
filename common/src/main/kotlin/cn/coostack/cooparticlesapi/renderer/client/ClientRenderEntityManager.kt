package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectGraph
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
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
    private var cachedTickDelta = 0f
    private val cachedViewMatrix = Matrix4f()
    private val cachedProjMatrix = Matrix4f()
    private val renderStateGuard = RenderStateGuard()

    fun init() {
    }

    fun getFrom(uuid: UUID): RenderEntityInstance<RenderEntity>? {
        return entities[uuid]
    }

    fun clear() {
        entities.values.forEach { it.release() }
        entities.clear()
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        ClientPersistentBloomManager.clear()
        ClientScreenGlowManager.clear()
        ClientWorldLightManager.clear()
    }

    fun onShaderReload() {
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        ClientPersistentBloomManager.clear()
        ClientScreenGlowManager.clear()
        entities.values.forEach { instance ->
            instance.reinitialize()
        }
    }

    fun add(instance: RenderEntityInstance<RenderEntity>) {
        instance.entity.world = minecraft.level
        instance.entity.lastRenderPos = instance.entity.pos
        instance.initialize()
        entities[instance.entity.uuid] = instance
    }

    fun renderTick(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderWorldPass(tickDelta, viewMatrix, projMatrix)
    }

    fun renderWorldPass(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        val stack = Matrix4fStack(16)
        entities.values.forEach { instance ->
            val entity = instance.entity
            stack.pushMatrix()
            RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
            instance.renderLocal(tickDelta, viewMatrix, projMatrix, stack, renderStateGuard)
            stack.popMatrix()
            entity.lastRenderPos = entity.pos
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
        cachedTickDelta = 0f
    }

    fun runFramePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST)) {
            return
        }
        val graph = RenderEffectGraph(context.backend.capabilities)
        entities.values.forEach { instance ->
            instance.collectRenderContributions(context, graph)
        }
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
                instance.release()
                iterator.remove()
            }
        }
    }
}
