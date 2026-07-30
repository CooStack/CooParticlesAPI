package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectGraph
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.runtime.ClientRenderEntityRegistry
import cn.coostack.cooparticlesapi.renderer.runtime.LegacyRenderEntityRenderer
import cn.coostack.cooparticlesapi.renderer.runtime.RenderEntityInstance
import cn.coostack.cooparticlesapi.renderer.state.RenderStateGuard
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.Minecraft.getInstance
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
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
    private val entityPipeTypes = HashMap<ResourceLocation, ResourceLocation>()

    fun register(id: ResourceLocation, codec: StreamCodec<FriendlyByteBuf, RenderEntity>) {
        val existing = ClientRenderEntityRegistry.get(id)
        if (existing == null) {
            ClientRenderEntityRegistry.register(id, codec) { LegacyRenderEntityRenderer() }
            return
        }
        if (existing.rendererFactory == null) {
            ClientRenderEntityRegistry.registerRenderer(id) { LegacyRenderEntityRenderer() }
        }
    }

    fun register(entity: RenderEntity) {
        register(entity.getRenderID(), entity.getCodec())
    }

    fun bindEntityRenderPipe(type: ResourceLocation, pipeID: ResourceLocation) {
        entityPipeTypes[type] = pipeID
    }

    fun getPipeIDFromType(type: ResourceLocation): ResourceLocation {
        return entityPipeTypes[type] ?: ShaderPipeManagers.default.pipeID
    }

    fun getCodecFromID(id: ResourceLocation): StreamCodec<FriendlyByteBuf, RenderEntity>? {
        return ClientRenderEntityRegistry.get(id)?.codec
    }

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
        entities.values.forEach { it.release() }
        entities.clear()
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        CooPostEffects.client.clear()
    }

    fun onShaderReload() {
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
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

    fun renderRenderTypePass(
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        irisShaderPackInUse: Boolean
    ) {
        entities.values.forEach { instance ->
            val entity = instance.entity
            instance.beginWorldRenderFrame()
            MinecraftRendererUtil.transformTo(camera, entity.renderPosition(tickDelta), poseStack) {
                instance.renderRenderType(
                    tickDelta,
                    viewMatrix,
                    projMatrix,
                    this,
                    bufferSource,
                    camera,
                    irisShaderPackInUse
                )
            }
        }
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
        cachedTickDelta = 0f
    }

    fun runFramePost(context: RenderFrameContext) {
        if (!context.backend.supports(RenderBackendCapability.FINAL_FRAME_POST)) {
            return
        }
        val graph = RenderEffectGraph(context.backend.capabilities, context)
        entities.values.forEach { instance ->
            instance.collectRenderContributions(context, graph)
        }
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
                instance.release()
                iterator.remove()
            }
        }
        CooPostEffects.client.tick()
    }

    private fun RenderEntity.renderPosition(tickDelta: Float): Vec3 {
        val last = lastRenderPos
        val current = pos
        return Vec3(
            Mth.lerp(tickDelta.toDouble(), last.x, current.x),
            Mth.lerp(tickDelta.toDouble(), last.y, current.y),
            Mth.lerp(tickDelta.toDouble(), last.z, current.z)
        )
    }
}
