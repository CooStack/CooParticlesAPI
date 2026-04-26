package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

sealed interface PostEffectBinding {
    fun write(buf: FriendlyByteBuf)

    data object Screen : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) = Unit
    }

    data class ScreenPoint(val x: Float, val y: Float) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    data class WorldPos(val level: ResourceLocation?, val x: Double, val y: Double, val z: Double) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    data class Entity(val entityId: Int) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(entityId)
        }
    }

    data class Player(val playerId: UUID) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUUID(playerId)
        }
    }

    data class Block(val level: ResourceLocation?, val pos: BlockPos, val offset: PostEffectParamValue.Vec3? = null) :
        PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeBlockPos(pos)
            buf.writeBoolean(offset != null)
            offset?.write(buf)
        }
    }

    data class Item(val itemId: ResourceLocation?, val context: PostEffectItemContext) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(itemId != null)
            itemId?.let(buf::writeResourceLocation)
            buf.writeUtf(context.name)
        }
    }

    data class Custom(val key: ResourceLocation, val payload: ByteArray = ByteArray(0)) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeResourceLocation(key)
            buf.writeByteArray(payload)
        }

        override fun equals(other: Any?): Boolean {
            return other is Custom && key == other.key && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * key.hashCode() + payload.contentHashCode()
    }

    companion object {
        fun writeTyped(buf: FriendlyByteBuf, binding: PostEffectBinding) {
            buf.writeUtf(binding.typeId)
            binding.write(buf)
        }

        fun readTyped(buf: FriendlyByteBuf): PostEffectBinding {
            return when (val type = buf.readUtf()) {
                "screen" -> Screen
                "screen_point" -> ScreenPoint(buf.readFloat(), buf.readFloat())
                "world" -> WorldPos(readNullableId(buf), buf.readDouble(), buf.readDouble(), buf.readDouble())
                "entity" -> Entity(buf.readInt())
                "player" -> Player(buf.readUUID())
                "block" -> {
                    val level = readNullableId(buf)
                    val pos = buf.readBlockPos()
                    val offset = if (buf.readBoolean()) {
                        PostEffectParamValue.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                    } else {
                        null
                    }
                    Block(level, pos, offset)
                }

                "item" -> Item(readNullableId(buf), PostEffectItemContext.valueOf(buf.readUtf()))
                "custom" -> Custom(buf.readResourceLocation(), buf.readByteArray())
                else -> error("Unknown post effect binding type: $type")
            }
        }

        private fun readNullableId(buf: FriendlyByteBuf): ResourceLocation? {
            return if (buf.readBoolean()) buf.readResourceLocation() else null
        }
    }
}

val PostEffectBinding.typeId: String
    get() = when (this) {
        is PostEffectBinding.Screen -> "screen"
        is PostEffectBinding.ScreenPoint -> "screen_point"
        is PostEffectBinding.WorldPos -> "world"
        is PostEffectBinding.Entity -> "entity"
        is PostEffectBinding.Player -> "player"
        is PostEffectBinding.Block -> "block"
        is PostEffectBinding.Item -> "item"
        is PostEffectBinding.Custom -> "custom"
    }

enum class PostEffectItemContext {
    GUI,
    FIRST_PERSON_HAND,
    THIRD_PERSON_HAND,
    WORLD_ENTITY
}
