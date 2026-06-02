package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import io.netty.buffer.Unpooled
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * SerializableData wrapper for DisplayEntity emitters entry.
 */
class DisplayEntityEmittersData(
    var entityType: String = "",
    var entityData: ByteArray = ByteArray(0),
    var visibleRange: Float = 256f
) : SerializableData {
    constructor(entity: DisplayEntity) : this() {
        setEntity(entity)
    }

    companion object {
        @JvmStatic
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, DisplayEntityEmittersData> =
            StreamCodec.of(
                { buf, data ->
                    buf.writeUtf(data.entityType)
                    buf.writeInt(data.entityData.size)
                    buf.writeBytes(data.entityData)
                    buf.writeFloat(data.visibleRange)
                },
                { buf ->
                    val type = buf.readUtf()
                    val size = buf.readInt()
                    val bytes = ByteArray(size)
                    buf.readBytes(bytes)
                    val visibleRange = buf.readFloat()
                    DisplayEntityEmittersData(type, bytes, visibleRange)
                }
            )

        @JvmStatic
        fun fromEntity(entity: DisplayEntity): DisplayEntityEmittersData {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            entity.getCodec().encode(buf, entity)
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            return DisplayEntityEmittersData(entity::class.java.name, data)
        }
    }

    private var template: DisplayEntity? = null
    private var preparedControler: DisplayEntity? = null

    private fun decodeEntity(): DisplayEntity {
        val codec = DisplayEntityManager.registeredTypes[entityType]
            ?: throw IllegalStateException("DisplayEntity codec not registered for type: $entityType")
        return codec.decode(FriendlyByteBuf(Unpooled.wrappedBuffer(entityData.copyOf())))
    }

    private fun cloneEntity(entity: DisplayEntity): DisplayEntity {
        val type = entity::class.java
        val ins = runCatching {
            type.getDeclaredConstructor(Vec3::class.java, Level::class.java)
                .apply { isAccessible = true }
                .newInstance(entity.pos, entity.world)
        }.getOrNull() ?: type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

        return ins.apply { update(entity) }
    }

    private fun resolveEntity(): DisplayEntity {
        return template ?: decodeEntity().also { template = it }
    }

    private fun createEntity(): DisplayEntity {
        return cloneEntity(resolveEntity())
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, out SerializableData> {
        return PACKET_CODEC
    }

    override fun clone(): SerializableData {
        val entity = runCatching { cloneEntity(resolveEntity()) }.getOrNull()
        return if (entity != null) {
            DisplayEntityEmittersData(entity).also { it.visibleRange = visibleRange }
        } else {
            DisplayEntityEmittersData(entityType, entityData.copyOf(), visibleRange)
        }
    }

    override fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*> {
        val entity = createEntity()
        entity.world = world
        entity.pos = pos
        preparedControler = entity
        return entity
    }

    override fun getDisplayer(): ParticleDisplayer {
        val entity = preparedControler ?: createEntity().also { preparedControler = it }
        preparedControler = null
        return ParticleDisplayer.withDisplayEntity(entity)
    }

    fun setEntity(entity: DisplayEntity): DisplayEntityEmittersData {
        val clone = cloneEntity(entity)
        val data = fromEntity(clone)
        entityType = data.entityType
        entityData = data.entityData
        template = clone
        preparedControler = null
        return this
    }
}
