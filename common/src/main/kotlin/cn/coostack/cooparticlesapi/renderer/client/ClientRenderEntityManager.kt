package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.exceptions.RenderPipeNotFoundException
import cn.coostack.cooparticlesapi.renderer.RenderEntity
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
    private val entitiesPipeClassifier = HashMap<ResourceLocation, HashSet<RenderEntity>>()

    /**
     * key 是对应 RenderEntity的id
     * value 是 pipeID
     */
    private val entityPipeType = HashMap<ResourceLocation, ResourceLocation>()
    private val entityCodecs = HashMap<ResourceLocation, StreamCodec<FriendlyByteBuf, RenderEntity>>()
    fun getFrom(uuid: UUID): RenderEntity? {
        return entities[uuid]
    }

    fun register(entity: RenderEntity) {
        entityCodecs[entity.getRenderID()] = entity.getCodec()
    }

    fun bindEntityRenderPipe(type: ResourceLocation, pipeID: ResourceLocation) {
        entityPipeType[type] = pipeID
    }

    fun register(id: ResourceLocation, codec: StreamCodec<FriendlyByteBuf, RenderEntity>) {
        entityCodecs[id] = codec
    }

    fun getCodecFromID(id: ResourceLocation): StreamCodec<FriendlyByteBuf, RenderEntity>? {
        return entityCodecs[id]
    }

    fun clear() {
        entities.onEach {
            it.value.release()
        }.clear()
        entitiesPipeClassifier.onEach {
            for (entity in it.value) {
                entity.release()
            }
        }.clear()
    }

    fun add(entity: RenderEntity) {
        entity.init()
        entities[entity.uuid] = entity
        val pipe = getPipeIDFromType(entity.getRenderID())
        entitiesPipeClassifier.getOrPut(pipe) { HashSet() }.add(entity)
    }

    fun getPipeIDFromType(type: ResourceLocation): ResourceLocation {
        return entityPipeType[type] ?: ShaderPipeManagers.default.pipeID
    }

    fun renderTick(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        val window = minecraft.window
        if (window.screenWidth != minecraft.mainRenderTarget.viewWidth || window.screenHeight != minecraft.mainRenderTarget.viewHeight) {
            return
        }

        val stack = Matrix4fStack(16)
        entitiesPipeClassifier.forEach {
            val pipeID = it.key
            val entities = it.value
            val pipe = ClientRenderPipelineManager.getPipeManager(pipeID)!!
            pipe.writeFrame {
                entities.forEach { entity ->
                    stack.pushMatrix()
                    RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
                    entity.renderOnWorld(stack, viewMatrix, projMatrix, tickDelta)
                    stack.popMatrix()
                }
            }
            pipe.render()
        }
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
                entitiesPipeClassifier[targetPipe]?.remove(entity)
                entity.release()
                iterator.remove()
            }
        }
    }
}