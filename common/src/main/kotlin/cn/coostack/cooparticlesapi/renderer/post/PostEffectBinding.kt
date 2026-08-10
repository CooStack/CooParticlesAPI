package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * 描述一个 post effect 实例“绑定到哪里”。
 *
 * 绑定不是输入 texture，而是效果的空间语义。OpenGL backend 会根据 binding 计算内置 uniform：
 *
 * - `center`：屏幕归一化坐标，供 shockwave / halo / distortion 这类局部效果使用
 * - `sourceDepth`：绑定点投影后的深度，供深度裁剪或遮挡逻辑使用
 *
 * 这个类型替代了调用方在每个 shader 里重复实现“实体/方块/世界坐标投影到屏幕坐标”的样板。
 */
internal sealed interface PostEffectBinding {
    fun write(buf: FriendlyByteBuf)

    /** 绑定整屏效果，`center` 默认为屏幕中心。 */
    data object Screen : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) = Unit
    }

    /** 绑定屏幕归一化坐标，x/y 通常位于 0..1。 */
    data class ScreenPoint(val x: Float, val y: Float) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    /** 绑定世界坐标。`level == null` 时表示客户端当前世界。 */
    data class WorldPos(val level: ResourceLocation?, val x: Double, val y: Double, val z: Double) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    /** 绑定实体 id，客户端会在当前 level 中查找实体并投影其中心位置。 */
    data class Entity(val entityId: Int) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(entityId)
        }
    }

    /** 绑定玩家 UUID，适合跨维度/多人场景中明确指定玩家。 */
    data class Player(val playerId: UUID) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUUID(playerId)
        }
    }

    /**
     * 绑定方块位置。
     *
     * [offset] 是方块内偏移，默认使用方块中心 `(0.5, 0.5, 0.5)`。
     * 复杂例子：绑定到方块顶面中心可传 `Vec3Value(0.5, 1.0, 0.5)`。
     */
    data class Block(val level: ResourceLocation?, val pos: BlockPos, val offset: PostEffectParamValue.Vec3Value? = null) :
        PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeBlockPos(pos)
            buf.writeBoolean(offset != null)
            offset?.write(buf)
        }
    }

    /** 绑定物品语义。当前默认投影到屏幕中心，保留给 item GUI / hand / world item 扩展。 */
    data class Item(val itemId: ResourceLocation?, val context: PostEffectItemContext) : PostEffectBinding {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(itemId != null)
            itemId?.let(buf::writeResourceLocation)
            buf.writeUtf(context.name)
        }
    }

    /**
     * 自定义绑定。
     *
     * 用于框架未覆盖的业务定位方式。payload 会被同步，但默认 OpenGL backend 不理解业务含义；
     * 需要自定义 descriptor/executor 或在 shader 参数中额外传递所需坐标。
     */
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
                        PostEffectParamValue.Vec3Value(buf.readDouble(), buf.readDouble(), buf.readDouble())
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

internal val PostEffectBinding.typeId: String
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

/** 物品绑定的语义位置。当前默认 backend 只保留语义，后续可扩展为不同投影方式。 */
internal enum class PostEffectItemContext {
    /** 物品在 GUI 中渲染，例如背包或 JEI 类界面。 */
    GUI,
    /** 第一人称手持物品。 */
    FIRST_PERSON_HAND,
    /** 第三人称手持物品。 */
    THIRD_PERSON_HAND,
    /** 掉落物或物品实体。 */
    WORLD_ENTITY
}
