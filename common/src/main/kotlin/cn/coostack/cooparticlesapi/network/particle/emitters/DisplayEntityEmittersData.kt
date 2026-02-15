package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import io.netty.buffer.Unpooled
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * SerializableData wrapper for DisplayEntity emitters entry.
 */
class DisplayEntityEmittersData(
    var entityType: String = "",
    var entityData: ByteArray = ByteArray(0)
) : SerializableData {
    constructor(entity: DisplayEntity) : this() {
        setEntity(entity)
    }

    companion object {
        @JvmStatic
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayEntityEmittersData> =
            StreamCodec.of(
                { buf, data ->
                    buf.writeUtf(data.entityType)
                    buf.writeInt(data.entityData.size)
                    buf.writeBytes(data.entityData)
                },
                { buf ->
                    val type = buf.readUtf()
                    val size = buf.readInt()
                    val bytes = ByteArray(size)
                    buf.readBytes(bytes)
                    DisplayEntityEmittersData(type, bytes)
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

    private var prepared: DisplayEntity? = null

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
        return prepared ?: decodeEntity().also { prepared = it }
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, out SerializableData> {
        return PACKET_CODEC
    }

    override fun clone(): SerializableData {
        val entity = runCatching { cloneEntity(resolveEntity()) }.getOrNull()
        return if (entity != null) {
            DisplayEntityEmittersData(entity)
        } else {
            DisplayEntityEmittersData(entityType, entityData.copyOf())
        }
    }

    override fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*> {
        val entity = resolveEntity()
        entity.world = world
        entity.pos = pos
        prepared = entity
        return entity
    }

    override fun getDisplayer(): ParticleDisplayer {
        val entity = resolveEntity()
        return ParticleDisplayer.withDisplayEntity(entity)
    }

    fun setEntity(entity: DisplayEntity): DisplayEntityEmittersData {
        val clone = cloneEntity(entity)
        val data = fromEntity(clone)
        entityType = data.entityType
        entityData = data.entityData
        prepared = clone
        return this
    }
}
