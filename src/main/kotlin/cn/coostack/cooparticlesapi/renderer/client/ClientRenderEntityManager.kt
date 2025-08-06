package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import java.util.UUID
import kotlin.collections.iterator

@Environment(EnvType.CLIENT)
object ClientRenderEntityManager {
    private val entities = HashMap<UUID, RenderEntity>()
    private val entityCodecs = HashMap<Identifier, PacketCodec<PacketByteBuf, RenderEntity>>()
    fun getFrom(uuid: UUID): RenderEntity? {
        return entities[uuid]
    }

    fun register(entity: RenderEntity) {
        entityCodecs[entity.getRenderID()] = entity.getCodec()
    }

    fun register(id: Identifier, codec: PacketCodec<PacketByteBuf, RenderEntity>) {
        entityCodecs[id] = codec
    }

    fun getCodecFromID(id: Identifier): PacketCodec<PacketByteBuf, RenderEntity>? {
        return entityCodecs[id]
    }

    fun clear() {
        entities.onEach {
            it.value.release()
        }.clear()
    }

    fun add(entity: RenderEntity) {
        entity.init()
        entities[entity.uuid] = entity
    }

    fun renderTick(tickDelta: Float, viewMatrix: Matrix4f, projMatrix: Matrix4f) {
        val stack = MatrixStack()
        for (entry in entities) {
            val entity = entry.value
            stack.push()
            RenderUtil.setRenderStackWithEntity(stack, entity, tickDelta)
            entity.renderOnWorld(stack, viewMatrix, projMatrix, tickDelta)
            stack.pop()
        }
    }

    fun tick() {
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = entry.value
            entity.tick()
            if (entity.canceled) {
                entity.release()
                iterator.remove()
            }
        }
    }
}