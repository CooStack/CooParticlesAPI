package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * post effect 参数值的网络可序列化封装。
 *
 * 这些值用于普通 uniform，也可用于特殊输入：
 *
 * - `FloatValue` / `IntValue` 等会上传到同名 uniform
 * - `ResourceValue` 可作为 [PostEffectPassBuilder.inputCustomTexture] 的资源贴图来源
 * - `IntValue` / `LongValue` 也可作为 [PostEffectPassBuilder.inputCustomTexture] 的已有 GL texture id
 * - `Vec3Value` 常用于方块绑定偏移或 `vec3` uniform
 *
 * 类型名统一带 `Value` 后缀，是为了避免和 Minecraft 的 `Vec3`、`ResourceLocation`、
 * JOML 的 vector 类型以及 Java/Kotlin 常见类型重名。
 *
 * 网络 type id 仍保持 `"vec3"`、`"color"` 这类短字符串，改类名不会改变协议格式。
 */
sealed interface PostEffectParamValue {
    fun write(buf: FriendlyByteBuf)

    /** 布尔参数，通常对应 GLSL `uniform bool`，例如 `throughWalls`。 */
    data class BoolValue(val value: Boolean) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(value)
        }
    }

    /**
     * 整数参数。
     *
     * 普通 uniform 场景对应 GLSL `uniform int`；当参数名匹配
     * [PostEffectPassBuilder.inputCustomTexture] 的 sampler 名时，也可以表示已有 GL texture id。
     */
    data class IntValue(val value: Int) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(value)
        }
    }

    /**
     * 长整数参数。
     *
     * 普通 uniform 上传时会按 backend 规则转换；作为 custom texture 参数时可用于容纳较宽的
     * 原生 texture handle，再在 OpenGL backend 中截断为当前 GL id。
     */
    data class LongValue(val value: Long) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeLong(value)
        }
    }

    /** 浮点参数，最常用于强度、半径、阈值、时间进度等 `uniform float`。 */
    data class FloatValue(val value: Float) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(value)
        }
    }

    /** 双精度参数；上传到 shader 时通常会降为 float，适合网络侧保留更高精度的业务值。 */
    data class DoubleValue(val value: Double) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(value)
        }
    }

    /** 字符串参数。默认 OpenGL backend 不上传字符串，主要给自定义 executor 或业务标记使用。 */
    data class StringValue(val value: String) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(value)
        }
    }

    /**
     * 资源位置参数。
     *
     * 最常见用途是作为 [PostEffectPassBuilder.inputCustomTexture] 的贴图来源：
     *
     * ```kotlin
     * inputCustomTexture("noise", textureSlot = 3)
     * params { resource("noise", id("textures/effect/noise.png")) }
     * ```
     */
    data class ResourceValue(val value: ResourceLocation) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeResourceLocation(value)
        }
    }

    /** 二维向量参数，通常对应 GLSL `uniform vec2`，例如方向、屏幕尺寸、UV 偏移。 */
    data class Vec2Value(val x: Float, val y: Float) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(x)
            buf.writeFloat(y)
        }
    }

    /**
     * 三维向量参数。
     *
     * 用作 uniform 时对应 GLSL `vec3`；用作 [PostEffectBinding.Block.offset] 时表示方块内偏移。
     * 类名带 `Value` 后缀，避免和 Minecraft `net.minecraft.world.phys.Vec3` 混淆。
     */
    data class Vec3Value(val x: Double, val y: Double, val z: Double) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    /** 颜色参数，通常对应 GLSL `uniform vec4`，分量范围一般按 0..1 传入。 */
    data class ColorValue(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(red)
            buf.writeFloat(green)
            buf.writeFloat(blue)
            buf.writeFloat(alpha)
        }
    }

    companion object {
        fun writeTyped(buf: FriendlyByteBuf, value: PostEffectParamValue) {
            buf.writeUtf(value.typeId)
            value.write(buf)
        }

        fun readTyped(buf: FriendlyByteBuf): PostEffectParamValue {
            return when (val type = buf.readUtf()) {
                "bool" -> BoolValue(buf.readBoolean())
                "int" -> IntValue(buf.readInt())
                "long" -> LongValue(buf.readLong())
                "float" -> FloatValue(buf.readFloat())
                "double" -> DoubleValue(buf.readDouble())
                "string" -> StringValue(buf.readUtf())
                "resource" -> ResourceValue(buf.readResourceLocation())
                "vec2" -> Vec2Value(buf.readFloat(), buf.readFloat())
                "vec3" -> Vec3Value(buf.readDouble(), buf.readDouble(), buf.readDouble())
                "color" -> ColorValue(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
                else -> error("Unknown post effect param type: $type")
            }
        }
    }
}

/**
 * 返回该参数在网络包中的类型 id。
 *
 * 这个 id 是协议字段，不等同于 Kotlin 类名。即使类名是 `Vec3Value`，
 * 网络中仍写 `"vec3"`，用于保持兼容和减少包体。
 */
val PostEffectParamValue.typeId: String
    get() = when (this) {
        is PostEffectParamValue.BoolValue -> "bool"
        is PostEffectParamValue.IntValue -> "int"
        is PostEffectParamValue.LongValue -> "long"
        is PostEffectParamValue.FloatValue -> "float"
        is PostEffectParamValue.DoubleValue -> "double"
        is PostEffectParamValue.StringValue -> "string"
        is PostEffectParamValue.ResourceValue -> "resource"
        is PostEffectParamValue.Vec2Value -> "vec2"
        is PostEffectParamValue.Vec3Value -> "vec3"
        is PostEffectParamValue.ColorValue -> "color"
    }

/**
 * 一个 post effect 实例携带的参数表。
 *
 * 参数表会随 [SyncedPostEffectState] 发送到客户端，并在每帧执行 pass 时被 uniform provider 读取。
 * 参数名通常和 shader uniform 名保持一致：
 *
 * ```kotlin
 * BuiltinPostEffectTypes.SHOCKWAVE.create()
 *     .duration(30)
 *     .params {
 *         float("radius", 0.35f)
 *         float("strength", 0.08f)
 *         bool("throughWalls", false)
 *     }
 * ```
 *
 * 这个类替代了“每个效果自己定义一个 packet payload / NBT / bytebuf 格式”的重复工作。
 */
data class PostEffectParams(
    private val values: Map<String, PostEffectParamValue> = emptyMap()
) {
    fun asMap(): Map<String, PostEffectParamValue> = values
    operator fun get(name: String): PostEffectParamValue? = values[name]
    fun plus(name: String, value: PostEffectParamValue): PostEffectParams = PostEffectParams(values + (name to value))

    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(values.size)
        values.toSortedMap().forEach { (name, value) ->
            buf.writeUtf(name)
            PostEffectParamValue.writeTyped(buf, value)
        }
    }

    companion object {
        val EMPTY = PostEffectParams()

        fun read(buf: FriendlyByteBuf): PostEffectParams {
            val count = buf.readInt()
            val values = LinkedHashMap<String, PostEffectParamValue>(count)
            repeat(count) {
                values[buf.readUtf()] = PostEffectParamValue.readTyped(buf)
            }
            return PostEffectParams(values)
        }
    }
}

/**
 * [PostEffectParams] 的构建器。
 *
 * 自定义 post 实例中用户最常用的是：
 *
 * ```kotlin
 * MyPost.create()
 *     .params {
 *         float("strength", 0.08f)
 *         color("tint", 0.4f, 0.8f, 1.0f)
 *         resource("noise", id("textures/effect/noise.png"))
 *     }
 * ```
 *
 * 这里替代手写 `mapOf("x" to PostEffectParamValue.FloatValue(...))`，并把网络可序列化类型集中约束在一处。
 */
class PostEffectParamsBuilder {
    private val values = LinkedHashMap<String, PostEffectParamValue>()

    /** 写入 `bool` 参数，通常对应 GLSL `uniform bool`。 */
    fun bool(name: String, value: Boolean) = apply { values[name] = PostEffectParamValue.BoolValue(value) }
    /** 写入 `int` 参数，通常对应 GLSL `uniform int`，也可作为 custom texture 的 GL texture id。 */
    fun int(name: String, value: Int) = apply { values[name] = PostEffectParamValue.IntValue(value) }
    /** 写入 `long` 参数；上传到 shader 时会按当前 backend 逻辑转成 float 或 texture id。 */
    fun long(name: String, value: Long) = apply { values[name] = PostEffectParamValue.LongValue(value) }
    /** 写入 `float` 参数，是 post effect 最常用的强度、半径、阈值类型。 */
    fun float(name: String, value: Float) = apply { values[name] = PostEffectParamValue.FloatValue(value) }
    /** 写入 `double` 参数；上传 shader 时会转为 float。 */
    fun double(name: String, value: Double) = apply { values[name] = PostEffectParamValue.DoubleValue(value) }
    /** 写入字符串参数；当前 OpenGL uniform 上传会忽略它，通常用于自定义 executor 或业务标记。 */
    fun string(name: String, value: String) = apply { values[name] = PostEffectParamValue.StringValue(value) }
    /** 写入资源位置，常用于 [PostEffectPassBuilder.inputCustomTexture]。 */
    fun resource(name: String, value: ResourceLocation) = apply { values[name] = PostEffectParamValue.ResourceValue(value) }
    /** 写入二维向量，通常对应 GLSL `uniform vec2`。 */
    fun vec2(name: String, x: Float, y: Float) = apply { values[name] = PostEffectParamValue.Vec2Value(x, y) }
    /** 写入三维向量，通常对应 GLSL `uniform vec3` 或方块绑定偏移。 */
    fun vec3(name: String, x: Double, y: Double, z: Double) = apply { values[name] = PostEffectParamValue.Vec3Value(x, y, z) }
    /** 写入颜色，通常对应 GLSL `uniform vec4`。 */
    fun color(name: String, red: Float, green: Float, blue: Float, alpha: Float = 1f) = apply {
        values[name] = PostEffectParamValue.ColorValue(red, green, blue, alpha)
    }

    /** 放入一个已经构造好的参数值，适合调用方做复用或封装。 */
    fun put(name: String, value: PostEffectParamValue) = apply { values[name] = value }
    fun build(): PostEffectParams = PostEffectParams(values.toMap())
}
