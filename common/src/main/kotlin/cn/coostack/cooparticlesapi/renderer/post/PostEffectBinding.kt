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
    /**
     * 写入绑定类型对应的数据字段。
     *
     * 该方法只写实现自身的数据，不写类型 id；需要跨网络传输时应调用 [writeTyped]。
     * 读端必须使用相同的字段顺序，否则后续包字段会发生错位。
     *
     * @param buf 目标网络缓冲区
     */
    fun write(buf: FriendlyByteBuf)

    /** 绑定整屏效果，`center` 默认为屏幕中心。 */
    data object Screen : PostEffectBinding {
        /**
         * 按 `Screen` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) = Unit
    }

    /**
     * 绑定屏幕归一化坐标，x/y 通常位于 0..1。
     *
     * @property x 水平屏幕坐标，0 表示左侧，1 表示右侧
     * @property y 垂直屏幕坐标，0 表示顶部，1 表示底部
     * 示例：`PostEffectBinding.ScreenPoint(0.5F, 0.5F)` 绑定屏幕中心。
     */
    data class ScreenPoint(val x: Float, val y: Float) : PostEffectBinding {
        /**
         * 按 `ScreenPoint` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    /**
     * 绑定世界坐标。`level == null` 时表示客户端当前世界。
     *
     * @property level 维度资源位置；为空时使用当前客户端世界
     * @property x 世界 X 坐标
     * @property y 世界 Y 坐标
     * @property z 世界 Z 坐标
     */
    data class WorldPos(val level: ResourceLocation?, val x: Double, val y: Double, val z: Double) : PostEffectBinding {
        /**
         * 按 `WorldPos` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    /**
     * 绑定实体 id，客户端会在当前 level 中查找实体并投影其中心位置。
     *
     * @property entityId 当前世界中实体的运行时 id
     * 示例：`PostEffectBinding.Entity(entity.id)`。
     */
    data class Entity(val entityId: Int) : PostEffectBinding {
        /**
         * 按 `Entity` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(entityId)
        }
    }

    /**
     * 绑定玩家 UUID，适合跨维度/多人场景中明确指定玩家。
     *
     * @property playerId 玩家持久 UUID；客户端按 UUID 查找在线玩家
     */
    data class Player(val playerId: UUID) : PostEffectBinding {
        /**
         * 按 `Player` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUUID(playerId)
        }
    }

    /**
     * 绑定方块位置。
     *
     * [offset] 是方块内偏移，默认使用方块中心 `(0.5, 0.5, 0.5)`。
     * 复杂例子：绑定到方块顶面中心可传 `Vec3Value(0.5, 1.0, 0.5)`。
     *
     * @property level 维度资源位置；为空时使用客户端当前世界
     * @property pos 方块的整数坐标
     * @property offset 方块内偏移；为空时由 backend 使用方块中心
     */
    data class Block(val level: ResourceLocation?, val pos: BlockPos, val offset: PostEffectParamValue.Vec3Value? = null) :
        PostEffectBinding {
        /**
         * 按 `Block` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(level != null)
            level?.let(buf::writeResourceLocation)
            buf.writeBlockPos(pos)
            buf.writeBoolean(offset != null)
            offset?.write(buf)
        }
    }

    /**
     * 绑定物品语义。当前默认投影到屏幕中心，保留给 item GUI / hand / world item 扩展。
     *
     * @property itemId 物品资源位置；为空时只保留 context 语义
     * @property context 物品所在的渲染上下文，会影响未来 backend 的投影选择
     */
    data class Item(val itemId: ResourceLocation?, val context: PostEffectItemContext) : PostEffectBinding {
        /**
         * 按 `Item` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
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
     *
     * @property key 自定义绑定类型的资源位置，读写双方必须约定其含义
     * @property payload 类型专用的二进制数据，默认为空数组
     */
    data class Custom(val key: ResourceLocation, val payload: ByteArray = ByteArray(0)) : PostEffectBinding {
        /**
         * 按 `Custom` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeResourceLocation(key)
            buf.writeByteArray(payload)
        }

        /**
         * 执行 `Custom` 定义的 `equals` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`equals(other = other)`。
         *
         * @param other 当前操作需要的输入值；其语义由方法名和所属组件共同限定
         *
         * @return 当前操作计算、更新或查询得到的结果
         */
        override fun equals(other: Any?): Boolean {
            return other is Custom && key == other.key && payload.contentEquals(other.payload)
        }

        /**
         * 执行 `Custom` 定义的 `hashCode` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`hashCode()`。
         *
         * @return 当前操作计算、更新或查询得到的结果
         */
        override fun hashCode(): Int = 31 * key.hashCode() + payload.contentHashCode()
    }

    companion object {
        /**
         * 写入绑定类型 id 和绑定数据。
         *
         * 示例：`PostEffectBinding.writeTyped(buf, PostEffectBinding.Screen)`。
         *
         * @param buf 目标网络缓冲区
         * @param binding 要序列化的绑定实例
         */
        fun writeTyped(buf: FriendlyByteBuf, binding: PostEffectBinding) {
            buf.writeUtf(binding.typeId)
            binding.write(buf)
        }

        /**
         * 从网络缓冲区读取绑定类型 id 并构造对应实现。
         *
         * @param buf 已定位到绑定类型 id 的网络缓冲区
         * @return 解码后的绑定实例
         * @throws IllegalStateException 类型 id 未注册时抛出
         */
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
