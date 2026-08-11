package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * post effect 参数值的网络可序列化封装。
 *
 * 这些值用于普通 uniform，也可用于特殊输入：
 *
 * - `FloatValue` / `IntValue` 等会上传到同名 uniform
 * - `ResourceValue` 可作为 Pipeline 自定义纹理端口的资源贴图来源
 * - `IntValue` / `LongValue` 也可表示已有 GL texture id
 * - `Vec3Value` 常用于方块绑定偏移或 `vec3` uniform
 *
 * 类型名统一带 `Value` 后缀，是为了避免和 Minecraft 的 `Vec3`、`ResourceLocation`、
 * JOML 的 vector 类型以及 Java/Kotlin 常见类型重名。
 *
 * 网络 type id 仍保持 `"vec3"`、`"color"` 这类短字符串，改类名不会改变协议格式。
 */
internal sealed interface PostEffectParamValue {
    /**
     * 按当前值类型的固定字段顺序写入网络缓冲区。
     *
     * 调用方通常应使用 [writeTyped]，让类型 id 和数据一起写入；直接调用时必须由读取端
     * 事先知道具体实现。示例：`PostEffectParamValue.FloatValue(0.8F).write(buf)`。
     *
     * @param buf 要写入的 Minecraft 网络缓冲区
     */
    fun write(buf: FriendlyByteBuf)

    /**
     * 布尔参数，通常对应 GLSL `uniform bool`，例如 `throughWalls`。
     *
     * @property value 要同步和上传的布尔值
     */
    data class BoolValue(val value: Boolean) : PostEffectParamValue {
        /**
         * 按 `BoolValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeBoolean(value)
        }
    }

    /**
     * 整数参数。
     *
     * 普通 uniform 场景对应 GLSL `uniform int`；匹配纹理端口 sampler 名时也可以表示已有 GL texture id。
     *
     * @property value 整数 uniform 或 OpenGL texture id
     */
    data class IntValue(val value: Int) : PostEffectParamValue {
        /**
         * 按 `IntValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeInt(value)
        }
    }

    /**
     * 长整数参数。
     *
     * 普通 uniform 上传时会按 backend 规则转换；作为 custom texture 参数时可用于容纳较宽的
     * 原生 texture handle，再在 OpenGL backend 中截断为当前 GL id。
     *
     * @property value 要同步的长整数值
     */
    data class LongValue(val value: Long) : PostEffectParamValue {
        /**
         * 按 `LongValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeLong(value)
        }
    }

    /**
     * 浮点参数，最常用于强度、半径、阈值、时间进度等 `uniform float`。
     *
     * @property value 要同步的单精度值
     */
    data class FloatValue(val value: Float) : PostEffectParamValue {
        /**
         * 按 `FloatValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(value)
        }
    }

    /**
     * 双精度参数；上传到 shader 时通常会降为 float，适合网络侧保留更高精度的业务值。
     *
     * @property value 网络侧保留的双精度值
     */
    data class DoubleValue(val value: Double) : PostEffectParamValue {
        /**
         * 按 `DoubleValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(value)
        }
    }

    /**
     * 字符串参数。默认 OpenGL backend 不上传字符串，主要给自定义 executor 或业务标记使用。
     *
     * @property value UTF-8 业务文本；不应存放无限长度或不可信的大块数据
     */
    data class StringValue(val value: String) : PostEffectParamValue {
        /**
         * 按 `StringValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(value)
        }
    }

    /**
     * 资源位置参数。
     *
     * 最常见用途是作为 Pipeline 自定义纹理端口的贴图来源：
     *
     * ```kotlin
     * inputCustomTexture("noise", textureSlot = 3)
     * params { resource("noise", id("textures/effect/noise.png")) }
     * ```
     *
     * @property value 要加载或同步的资源位置
     */
    data class ResourceValue(val value: ResourceLocation) : PostEffectParamValue {
        /**
         * 按 `ResourceValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeResourceLocation(value)
        }
    }

    /**
     * 二维向量参数，通常对应 GLSL `uniform vec2`，例如方向、屏幕尺寸、UV 偏移。
     *
     * @property x 第一分量
     * @property y 第二分量
     */
    data class Vec2Value(val x: Float, val y: Float) : PostEffectParamValue {
        /**
         * 按 `Vec2Value` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
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
     * 三维向量参数。
     *
     * 用作 uniform 时对应 GLSL `vec3`；用作 [PostEffectBinding.Block.offset] 时表示方块内偏移。
     * 类名带 `Value` 后缀，避免和 Minecraft `net.minecraft.world.phys.Vec3` 混淆。
     *
     * @property x X 分量；作为方块偏移时通常位于 0..1
     * @property y Y 分量；作为方块偏移时通常位于 0..1
     * @property z Z 分量；作为方块偏移时通常位于 0..1
     */
    data class Vec3Value(val x: Double, val y: Double, val z: Double) : PostEffectParamValue {
        /**
         * 按 `Vec3Value` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeDouble(x)
            buf.writeDouble(y)
            buf.writeDouble(z)
        }
    }

    /**
     * 颜色参数，通常对应 GLSL `uniform vec4`，分量范围一般按 0..1 传入。
     *
     * @property red 红色分量
     * @property green 绿色分量
     * @property blue 蓝色分量
     * @property alpha 透明度分量，默认完全不透明
     */
    data class ColorValue(val red: Float, val green: Float, val blue: Float, val alpha: Float = 1f) : PostEffectParamValue {
        /**
         * 按 `ColorValue` 约定的字段顺序写入 `write` 数据；读取端必须使用相同协议。
         *
         * 示例：`write(buf = buf)`。
         *
         * @param buf 承载本次读写数据的缓冲区，调用前必须位于约定字段起点
         */
        override fun write(buf: FriendlyByteBuf) {
            buf.writeFloat(red)
            buf.writeFloat(green)
            buf.writeFloat(blue)
            buf.writeFloat(alpha)
        }
    }

    /** 无符号值、双精度值、矩阵或数组等完整 GLSL uniform 参数。 */
    data class UniformValue(val value: CooUniformValue) : PostEffectParamValue {
        override fun write(buf: FriendlyByteBuf) {
            CooUniformValue.STREAM_CODEC.encode(buf, value)
        }
    }

    companion object {
        /**
         * 写入类型 id 和对应参数数据。
         *
         * 示例：`PostEffectParamValue.writeTyped(buf, PostEffectParamValue.IntValue(4))`。
         *
         * @param buf 目标网络缓冲区
         * @param value 要序列化的参数值
         */
        fun writeTyped(buf: FriendlyByteBuf, value: PostEffectParamValue) {
            buf.writeUtf(value.typeId)
            value.write(buf)
        }

        /**
         * 读取类型 id，并按协议构造匹配的参数实现。
         *
         * @param buf 已定位到类型 id 的网络缓冲区
         * @return 解码后的参数值
         * @throws IllegalStateException 类型 id 未注册时抛出
         */
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
                "uniform" -> UniformValue(CooUniformValue.STREAM_CODEC.decode(buf))
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
internal val PostEffectParamValue.typeId: String
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
        is PostEffectParamValue.UniformValue -> "uniform"
    }

/** 保留简单参数的原协议类型，并用通用类型承载其余 GLSL uniform。 */
internal fun CooUniformValue.toPostEffectParamValue(): PostEffectParamValue {
    return when (this) {
        is CooUniformValue.BoolValue -> PostEffectParamValue.BoolValue(value)
        is CooUniformValue.IntValue -> PostEffectParamValue.IntValue(value)
        is CooUniformValue.FloatValue -> PostEffectParamValue.FloatValue(value)
        is CooUniformValue.Vec2Value -> PostEffectParamValue.Vec2Value(x, y)
        is CooUniformValue.Vec3Value -> PostEffectParamValue.Vec3Value(x.toDouble(), y.toDouble(), z.toDouble())
        is CooUniformValue.Vec4Value -> PostEffectParamValue.ColorValue(x, y, z, w)
        is CooUniformValue.UIntValue,
        is CooUniformValue.DoubleValue,
        is CooUniformValue.IVecValue,
        is CooUniformValue.UVecValue,
        is CooUniformValue.BVecValue,
        is CooUniformValue.DVecValue,
        is CooUniformValue.MatValue,
        is CooUniformValue.DMatValue,
        is CooUniformValue.SamplerValue,
        is CooUniformValue.ImageValue,
        is CooUniformValue.ArrayValue -> PostEffectParamValue.UniformValue(this)
    }
}

/**
 * 一个 post effect 实例携带的参数表。
 *
 * 参数表会随 [SyncedPostEffectState] 发送到客户端，并在每帧执行 pass 时被 uniform provider 读取。
 * 参数名和 shader uniform 名保持一致；该快照也用于服务端到客户端的播放同步。
 */
internal data class PostEffectParams(
    private val values: Map<String, PostEffectParamValue> = emptyMap()
) {
    /**
     * 返回只读参数映射。
     *
     * 键是 shader uniform 或自定义纹理端口名称，值是对应的网络参数。
     */
    fun asMap(): Map<String, PostEffectParamValue> = values

    /**
     * 按名称查询参数。
     *
     * @param name shader uniform 或纹理参数名称
     * @return 已配置的参数；不存在时返回 null
     */
    operator fun get(name: String): PostEffectParamValue? = values[name]

    /**
     * 返回加入或替换一个参数后的不可变快照。
     *
     * @param name 参数名称
     * @param value 新参数值
     * @return 包含新映射的参数快照
     */
    fun plus(name: String, value: PostEffectParamValue): PostEffectParams = PostEffectParams(values + (name to value))

    /**
     * 按名称排序后写入全部参数，保证同一参数表产生稳定的网络顺序。
     *
     * @param buf 目标网络缓冲区
     */
    fun write(buf: FriendlyByteBuf) {
        buf.writeInt(values.size)
        values.toSortedMap().forEach { (name, value) ->
            buf.writeUtf(name)
            PostEffectParamValue.writeTyped(buf, value)
        }
    }

    companion object {
        val EMPTY = PostEffectParams()

        /**
         * 读取 [write] 写出的参数表。
         *
         * @param buf 已定位到参数数量字段的网络缓冲区
         * @return 解码后的不可变参数表
         */
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
 * 仅由 [cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffectPlayBuilder] 使用。
 */
internal class PostEffectParamsBuilder {
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
    /** 写入资源位置，供 Pipeline 自定义纹理端口读取。 */
    fun resource(name: String, value: ResourceLocation) = apply { values[name] = PostEffectParamValue.ResourceValue(value) }
    /** 写入二维向量，通常对应 GLSL `uniform vec2`。 */
    fun vec2(name: String, x: Float, y: Float) = apply { values[name] = PostEffectParamValue.Vec2Value(x, y) }
    /** 写入三维向量，通常对应 GLSL `uniform vec3` 或方块绑定偏移。 */
    fun vec3(name: String, x: Double, y: Double, z: Double) = apply { values[name] = PostEffectParamValue.Vec3Value(x, y, z) }
    /** 写入颜色，通常对应 GLSL `uniform vec4`。 */
    fun color(name: String, red: Float, green: Float, blue: Float, alpha: Float = 1F) = apply {
        values[name] = PostEffectParamValue.ColorValue(red, green, blue, alpha)
    }

    /** 放入一个已经构造好的参数值，适合调用方做复用或封装。 */
    fun put(name: String, value: PostEffectParamValue) = apply { values[name] = value }
    /**
     * 生成当前参数的不可变快照。
     *
     * 示例：`val params = PostEffectParamsBuilder().float("intensity", 1.5F).build()`。
     *
     * @return 与后续 builder 修改互不影响的参数表
     */
    fun build(): PostEffectParams = PostEffectParams(values.toMap())
}
