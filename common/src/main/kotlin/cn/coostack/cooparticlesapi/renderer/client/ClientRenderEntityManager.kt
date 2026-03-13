package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.exceptions.RenderPipeNotFoundException
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.RenderEntityRenderPass
import cn.coostack.cooparticlesapi.renderer.shader.pipe.manager.ShaderPipeManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.Minecraft.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import java.util.UUID
import kotlin.collections.iterator

object ClientRenderEntityManager {
    val minecraft: Minecraft = getInstance()
    private val entities = HashMap<UUID, RenderEntity>()
    private val entitiesPipeClassifier =
        LinkedHashMap<RenderEntityRenderPass, LinkedHashMap<ResourceLocation, LinkedHashSet<RenderEntity>>>()

    /**
     * key 是对应 RenderEntity的id
     * value 是 pipeID
     */
    private val entityPipeType = HashMap<ResourceLocation, ResourceLocation>()
    private val entityCodecs = HashMap<ResourceLocation, StreamCodec<FriendlyByteBuf, RenderEntity>>()
    private var postProcessPrepared = false
    private var frameStatePrepared = false
    private var cachedTickDelta = 0f
    private val cachedViewMatrix = Matrix4f()
    private val cachedProjMatrix = Matrix4f()

    private fun getClassifier(pass: RenderEntityRenderPass): LinkedHashMap<ResourceLocation, LinkedHashSet<RenderEntity>> {
        return entitiesPipeClassifier.getOrPut(pass) { LinkedHashMap() }
    }

    fun init() {
    }

    fun getFrom(uuid: UUID): RenderEntity? {
        return entities[uuid]
    }

    fun register(entity: RenderEntity) {
        entityCodecs[entity.getRenderID()] = entity.getCodec()
    }

    fun register(entity: RenderEntity, pipeID: ResourceLocation) {
        register(entity.getRenderID(), entity.getCodec(), pipeID)
    }

    fun bindEntityRenderPipe(type: ResourceLocation, pipeID: ResourceLocation) {
        entityPipeType[type] = pipeID
    }

    fun bindEntityRenderPipe(type: ResourceLocation, pipe: ShaderPipeManager) {
        bindEntityRenderPipe(type, pipe.pipeID)
    }

    fun register(id: ResourceLocation, codec: StreamCodec<FriendlyByteBuf, RenderEntity>) {
        entityCodecs[id] = codec
        bindEntityRenderPipe(id, ShaderPipeManagers.default.pipeID)
    }

    fun register(id: ResourceLocation, codec: StreamCodec<FriendlyByteBuf, RenderEntity>, pipeID: ResourceLocation) {
        entityCodecs[id] = codec
        bindEntityRenderPipe(id, pipeID)
    }

    fun getCodecFromID(id: ResourceLocation): StreamCodec<FriendlyByteBuf, RenderEntity>? {
        return entityCodecs[id]
    }

    fun clear() {
        entities.values.forEach {
            it.release()
        }
        entities.clear()
        entitiesPipeClassifier.values.forEach {
            it.clear()
        }
        postProcessPrepared = false
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        ClientPersistentBloomManager.clear()
        ClientScreenGlowManager.clear()
        ClientWorldLightManager.clear()
    }

    fun onShaderReload() {
        postProcessPrepared = false
        frameStatePrepared = false
        cachedTickDelta = 0f
        cachedViewMatrix.identity()
        cachedProjMatrix.identity()
        ClientPersistentBloomManager.clear()
        ClientScreenGlowManager.clear()
        entities.values.forEach { entity ->
            entity.init = false
            entity.init()
        }
    }

    fun add(entity: RenderEntity) {
        entity.world = minecraft.level
        entity.init()
        entities[entity.uuid] = entity
        val pipe = getPipeIDFromType(entity.getRenderID())
        getClassifier(entity.getRenderPass()).getOrPut(pipe) { LinkedHashSet() }.add(entity)
    }

    fun getPipeIDFromType(type: ResourceLocation): ResourceLocation {
        return entityPipeType[type] ?: ShaderPipeManagers.default.pipeID
    }

    fun renderTick(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderWorldPass(tickDelta, viewMatrix, projMatrix)
    }

    fun renderWorldPass(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        renderPass(RenderEntityRenderPass.WORLD, tickDelta, viewMatrix, projMatrix, true)
    }

    fun renderWorldLighting(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        ClientWorldLightManager.render(entities.values, tickDelta, viewMatrix, projMatrix)
    }

    fun renderScreenGlows(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        ClientScreenGlowManager.render(entities.values, tickDelta, viewMatrix, projMatrix)
    }

    fun renderPersistentBlooms(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        ClientPersistentBloomManager.render(entities.values, tickDelta, viewMatrix, projMatrix)
    }

    fun cacheFrameState(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        cachedTickDelta = tickDelta
        cachedViewMatrix.set(viewMatrix)
        cachedProjMatrix.set(projMatrix)
        frameStatePrepared = true
    }

    fun preparePostProcess(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        postProcessPrepared =
            renderPass(RenderEntityRenderPass.POST_PROCESS, tickDelta, viewMatrix, projMatrix, false)
    }

    fun flushFrameComposites() {
        if (frameStatePrepared) {
            renderWorldLighting(cachedTickDelta, cachedViewMatrix, cachedProjMatrix)
        }
        flushPostProcess()
        if (frameStatePrepared) {
            renderPersistentBlooms(cachedTickDelta, cachedViewMatrix, cachedProjMatrix)
        }
        if (frameStatePrepared) {
            renderScreenGlows(cachedTickDelta, cachedViewMatrix, cachedProjMatrix)
        }
        frameStatePrepared = false
        cachedTickDelta = 0f
    }

    fun flushPostProcess() {
        if (!postProcessPrepared) {
            return
        }
        minecraft.mainRenderTarget.bindWrite(false)
        for ((pipeID, bucket) in getClassifier(RenderEntityRenderPass.POST_PROCESS)) {
            if (bucket.isEmpty()) {
                continue
            }
            val pipe = ClientRenderPipelineManager.getPipeManager(pipeID) ?: let {
                throw RenderPipeNotFoundException(pipeID)
            }
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            pipe.render()
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
        }
        postProcessPrepared = false
    }

    private fun renderPass(
        pass: RenderEntityRenderPass,
        tickDelta: Float,
        viewMatrix: Matrix4f,
        projMatrix: Matrix4f,
        compositeNow: Boolean
    ): Boolean {
        val stack = Matrix4fStack(16)
        var rendered = false
        for ((pipeID, entities) in getClassifier(pass)) {
            if (entities.isEmpty()) {
                continue
            }
            val pipe = ClientRenderPipelineManager.getPipeManager(pipeID) ?: let {
                throw RenderPipeNotFoundException(pipeID)
            }
            rendered = true
            pipe.updateGlobalUniform("viewMat", viewMatrix)
            pipe.updateGlobalUniform("projMat", projMatrix)
            pipe.writeFrame {
                entities.forEach { entity ->
                    stack.pushMatrix()
                    RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
                    entity.renderOnWorld(stack, viewMatrix, projMatrix, tickDelta)
                    pipe.updateGlobalUniform("transMat", stack)
                    stack.popMatrix()
                }
            }
            if (compositeNow) {
                RenderSystem.depthMask(false)
                pipe.render()
                RenderSystem.depthMask(true)
            }
        }
        return rendered
    }

    fun tick() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = entry.value
            entity.tick()
            if (entity.canceled) {
                // 获取对标的管线分类器
                val targetPipe = entityPipeType[entity.getRenderID()] ?: let {
                    throw RenderPipeNotFoundException(entity.getRenderID())
                }
                getClassifier(entity.getRenderPass())[targetPipe]?.remove(entity)
                entity.release()
                iterator.remove()
            }
        }
    }
}
