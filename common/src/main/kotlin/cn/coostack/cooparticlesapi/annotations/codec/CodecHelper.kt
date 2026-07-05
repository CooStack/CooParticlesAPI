package cn.coostack.cooparticlesapi.annotations.codec

import cn.coostack.cooparticlesapi.CodecHelperJava
import cn.coostack.cooparticlesapi.animation.timeline.ValueConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.ValueConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.DoubleConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.DoubleConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.FloatConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.FloatConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.IntConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.IntConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.RelativeLocationConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.RelativeLocationConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.Vec3ConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.Vec3ConstTimeAnimator
import cn.coostack.cooparticlesapi.animation.timeline.Vector3fConstSpeedAnimator
import cn.coostack.cooparticlesapi.animation.timeline.Vector3fConstTimeAnimator
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.CompositionEmittersData
import cn.coostack.cooparticlesapi.network.particle.emitters.DisplayEntityEmittersData
import cn.coostack.cooparticlesapi.network.particle.emitters.SimpleRandomParticleData
import cn.coostack.cooparticlesapi.network.particle.data.DoubleRangeData
import cn.coostack.cooparticlesapi.network.particle.data.FloatRangeData
import cn.coostack.cooparticlesapi.network.particle.data.IntRangeData
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorDouble
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorFloat
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorVec3d
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorVector3f
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorRelativeLocation
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CodecHelper {
    val supposedTypes = ConcurrentHashMap<String, StreamCodec<out FriendlyByteBuf, *>>()

    init {
        CodecHelperJava.init()
        register(Short::class.java, StreamCodec.of({ buf, i -> buf.writeShort(i.toInt()) }, { it.readShort() }))
        register(Int::class.java, StreamCodec.of({ buf, i -> buf.writeInt(i) }, { it.readInt() }))
        register(Long::class.java, StreamCodec.of({ buf, i -> buf.writeLong(i) }, { it.readLong() }))
        register(LongArray::class.java, StreamCodec.of({ buf, i -> buf.writeLongArray(i) }, { it.readLongArray() }))
        register(Float::class.java, StreamCodec.of({ buf, i -> buf.writeFloat(i) }, { it.readFloat() }))
        register(Double::class.java, StreamCodec.of({ buf, i -> buf.writeDouble(i) }, { it.readDouble() }))
        register(String::class.java, StreamCodec.of({ buf, i -> buf.writeUtf(i) }, { it.readUtf() }))
        register(Byte::class.java, StreamCodec.of({ buf, i -> buf.writeByte(i.toInt()) }, { it.readByte() }))
        register(Boolean::class.java, StreamCodec.of({ buf, i -> buf.writeBoolean(i) }, { it.readBoolean() }))
        register(ByteArray::class.java, StreamCodec.of({ buf, i -> buf.writeByteArray(i) }, { it.readByteArray() }))
        register(Char::class.java, StreamCodec.of({ buf, i -> buf.writeChar(i.code) }, { it.readChar() }))
        register(UUID::class.java, StreamCodec.of({ buf, i -> buf.writeUUID(i) }, { it.readUUID() }))
        register(ControlableParticleData::class.java, ControlableParticleData.PACKET_CODEC)
        register(CompositionEmittersData::class.java, CompositionEmittersData.PACKET_CODEC)
        register(DisplayEntityEmittersData::class.java, DisplayEntityEmittersData.PACKET_CODEC)
        register(Vector3f::class.java, StreamCodec.of({ buf, i -> buf.writeVector3f(i) }, { it.readVector3f() }))
        register(Vector4f::class.java, StreamCodec.of({ buf, v ->
            buf.writeFloat(v.x)
            buf.writeFloat(v.y)
            buf.writeFloat(v.z)
            buf.writeFloat(v.w)
        }, {
            Vector4f(it.readFloat(), it.readFloat(), it.readFloat(), it.readFloat())
        }))
        register(Vec2::class.java, StreamCodec.of({ buf, i ->
            buf.writeFloat(i.x)
            buf.writeFloat(i.y)
        }, {
            Vec2(it.readFloat(), it.readFloat())
        }))
        register(Vec3::class.java, StreamCodec.of({ buf, i -> buf.writeVec3(i) }, { it.readVec3() }))
        register(Quaternionf::class.java, StreamCodec.of({ buf, q -> buf.writeQuaternion(q) }, { it.readQuaternion() }))
        register(AABB::class.java, StreamCodec.of({ buf, i ->
            buf.writeDouble(i.minX)
            buf.writeDouble(i.minY)
            buf.writeDouble(i.minZ)
            buf.writeDouble(i.maxX)
            buf.writeDouble(i.maxY)
            buf.writeDouble(i.maxZ)
        }, {
            AABB(it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble())
        }))
        register(HitBox::class.java, StreamCodec.of({ buf, i ->
            buf.writeDouble(i.x1)
            buf.writeDouble(i.y1)
            buf.writeDouble(i.z1)
            buf.writeDouble(i.x2)
            buf.writeDouble(i.y2)
            buf.writeDouble(i.z2)
        }, {
            HitBox(it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble())
        }))
        register(ItemStack::class.java, ItemStack.OPTIONAL_STREAM_CODEC)
        register(SimpleRandomParticleData::class.java, SimpleRandomParticleData.PACKET_CODEC)
        register(RelativeLocation::class.java, StreamCodec.of({ buf, r ->
            buf.apply {
                writeDouble(r.x)
                writeDouble(r.y)
                writeDouble(r.z)
            }
        }, { buf ->
            RelativeLocation(buf.readDouble(), buf.readDouble(), buf.readDouble())
        }))
        register(InterpolatorDouble::class.java, InterpolatorDouble.CODEC)
        register(InterpolatorFloat::class.java, InterpolatorFloat.CODEC)
        register(InterpolatorVec3d::class.java, InterpolatorVec3d.CODEC)
        register(InterpolatorVector3f::class.java, InterpolatorVector3f.CODEC)
        register(InterpolatorRelativeLocation::class.java, InterpolatorRelativeLocation.CODEC)
        register(
            DoubleRangeData::class.java,
            StreamCodec.of({ buf, i -> buf.writeDouble(i.min); buf.writeDouble(i.max) }, {
                DoubleRangeData(it.readDouble(), it.readDouble())
            })
        )
        register(
            IntRangeData::class.java,
            StreamCodec.of({ buf, i -> buf.writeInt(i.min); buf.writeInt(i.max) }, {
                IntRangeData(it.readInt(), it.readInt())
            })
        )
        register(
            FloatRangeData::class.java,
            StreamCodec.of({ buf, i -> buf.writeFloat(i.min); buf.writeFloat(i.max) }, {
                FloatRangeData(it.readFloat(), it.readFloat())
            })
        )
        register(
            BlockPos::class.java,
            StreamCodec.of(BlockPos.STREAM_CODEC::encode, BlockPos.STREAM_CODEC::decode)
        )
        register(
            BlockState::class.java,
            StreamCodec.of({ buf, s ->
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)
                    .encode(buf, s)
            }, { buf ->
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)
                    .decode(buf)
            })
        )
        register(
            ValueConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeDouble(i.targetNum.toDouble())
                buf.writeDouble(i.current.toDouble())
            }, {
                ValueConstTimeAnimator(it.readInt(), it.readDouble())
                    .resetCurrentTo(it.readDouble())
            })
        )
        register(
            ValueConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeDouble(i.speed.toDouble())
                buf.writeDouble(i.targetNum.toDouble())
                buf.writeDouble(i.current.toDouble())
            }, {
                ValueConstSpeedAnimator(it.readDouble(), it.readDouble())
                    .resetCurrentTo(it.readDouble())
            })
        )
        register(
            DoubleConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeDouble(i.targetNum)
                buf.writeDouble(i.current)
            }, {
                DoubleConstTimeAnimator(it.readInt(), it.readDouble())
                    .resetCurrentTo(it.readDouble())
            })
        )
        register(
            FloatConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeFloat(i.targetNum)
                buf.writeFloat(i.current)
            }, {
                FloatConstTimeAnimator(it.readInt(), it.readFloat())
                    .resetCurrentTo(it.readFloat())
            })
        )
        register(
            IntConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeInt(i.targetNum)
                buf.writeDouble(i.currentRaw)
            }, {
                IntConstTimeAnimator(it.readInt(), it.readInt())
                    .resetCurrentRawTo(it.readDouble())
            })
        )
        register(
            Vec3ConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeVec3(i.targetNum)
                buf.writeVec3(i.current)
            }, {
                Vec3ConstTimeAnimator(it.readInt(), it.readVec3())
                    .resetCurrentTo(it.readVec3())
            })
        )
        register(
            RelativeLocationConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeDouble(i.targetNum.x)
                buf.writeDouble(i.targetNum.y)
                buf.writeDouble(i.targetNum.z)
                buf.writeDouble(i.current.x)
                buf.writeDouble(i.current.y)
                buf.writeDouble(i.current.z)
            }, {
                RelativeLocationConstTimeAnimator(
                    it.readInt(),
                    RelativeLocation(it.readDouble(), it.readDouble(), it.readDouble())
                ).resetCurrentTo(RelativeLocation(it.readDouble(), it.readDouble(), it.readDouble()))
            })
        )
        register(
            Vector3fConstTimeAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.durationTick)
                buf.writeVector3f(i.targetNum)
                buf.writeVector3f(i.current)
            }, {
                Vector3fConstTimeAnimator(it.readInt(), it.readVector3f())
                    .resetCurrentTo(it.readVector3f())
            })
        )
        register(
            DoubleConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeDouble(i.speed)
                buf.writeDouble(i.targetNum)
                buf.writeDouble(i.current)
            }, {
                DoubleConstSpeedAnimator(it.readDouble(), it.readDouble())
                    .resetCurrentTo(it.readDouble())
            })
        )
        register(
            FloatConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeFloat(i.speed)
                buf.writeFloat(i.targetNum)
                buf.writeFloat(i.current)
            }, {
                FloatConstSpeedAnimator(it.readFloat(), it.readFloat())
                    .resetCurrentTo(it.readFloat())
            })
        )
        register(
            IntConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeInt(i.speed)
                buf.writeInt(i.targetNum)
                buf.writeDouble(i.currentRaw)
            }, {
                IntConstSpeedAnimator(it.readInt(), it.readInt())
                    .resetCurrentRawTo(it.readDouble())
            })
        )
        register(
            Vec3ConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeDouble(i.speed)
                buf.writeVec3(i.targetNum)
                buf.writeVec3(i.current)
            }, {
                Vec3ConstSpeedAnimator(it.readDouble(), it.readVec3())
                    .resetCurrentTo(it.readVec3())
            })
        )
        register(
            RelativeLocationConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeDouble(i.speed)
                buf.writeDouble(i.targetNum.x)
                buf.writeDouble(i.targetNum.y)
                buf.writeDouble(i.targetNum.z)
                buf.writeDouble(i.current.x)
                buf.writeDouble(i.current.y)
                buf.writeDouble(i.current.z)
            }, {
                RelativeLocationConstSpeedAnimator(
                    it.readDouble(),
                    RelativeLocation(it.readDouble(), it.readDouble(), it.readDouble())
                ).resetCurrentTo(RelativeLocation(it.readDouble(), it.readDouble(), it.readDouble()))
            })
        )
        register(
            Vector3fConstSpeedAnimator::class.java,
            StreamCodec.of({ buf, i ->
                buf.writeDouble(i.speed)
                buf.writeVector3f(i.targetNum)
                buf.writeVector3f(i.current)
            }, {
                Vector3fConstSpeedAnimator(it.readDouble(), it.readVector3f())
                    .resetCurrentTo(it.readVector3f())
            })
        )
    }

    /**
     * 编解码方式注册器
     *
     * 可以直接向 codec的 ByteBuf里写入 （大概就是writeInt这些）
     *
     * 示例:
     * ```kotlin
     * StreamCodec.of<FriendlyByteBuf, CustomOption>(
     *  { buf,option->
     *      // 这里假设option有2个参数 一个id: String 一个age：Int
     *      buf.writeUtf(option.id)
     *      buf.writeInt(option.age)
     *  },{
     *      CustomOption(it.readUtf(),it.readInt())
     *  }
     * )
     * ```
     *
     * @param T 要编码的类型
     * @param type 类型对应的类
     * @param codec 他的编解码器
     */
    fun <T> register(type: Class<T>, codec: StreamCodec<out FriendlyByteBuf, T>) {
        supposedTypes[type.name] = codec
    }

    /**
     * 转换为该list 基于该泛型的codec
     *
     * @param type
     */

    fun codecOf(type: Type): StreamCodec<out FriendlyByteBuf, *> {
        val codecType = normalizeCodecType(type)

        if (codecType is Class<*>) {
            return supposedTypes[codecType.name]
                ?: throw IllegalArgumentException("不支持的类型: ${codecType.name}")
        }

        if (codecType is ParameterizedType) {
            val raw = codecType.rawType as Class<*>

            if (List::class.java.isAssignableFrom(raw)) {
                return codecList(codecType)
            }

            if (Set::class.java.isAssignableFrom(raw)) {
                return codecSet(codecType)
            }

            if (Map::class.java.isAssignableFrom(raw)) {
                return codecMap(codecType)
            }
        }

        throw IllegalArgumentException("不支持的字段类型: $type")
    }

    private fun normalizeCodecType(type: Type): Type {
        if (type is WildcardType) {
            if (type.lowerBounds.isNotEmpty()) {
                throw IllegalArgumentException("不支持的字段类型: $type")
            }
            return type.upperBounds.firstOrNull() ?: Any::class.java
        }
        return type
    }

    @Suppress("UNCHECKED_CAST")
    fun codecList(type: Type): StreamCodec<out FriendlyByteBuf, *> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("List字段必须声明具体泛型: $type")
        }

        val elementType = type.actualTypeArguments[0]
        val elementCodec = codecOf(elementType) as StreamCodec<FriendlyByteBuf, Any>

        return StreamCodec.of<FriendlyByteBuf, List<*>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { element ->
                    elementCodec.encode(buf, element ?: error("List字段不支持null元素: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                val list = ArrayList<Any?>(size)
                repeat(size) {
                    list.add(elementCodec.decode(buf))
                }
                list
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun codecSet(type: Type): StreamCodec<out FriendlyByteBuf, *> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("Set字段必须声明具体泛型: $type")
        }

        val elementType = type.actualTypeArguments[0]
        val elementCodec = codecOf(elementType) as StreamCodec<FriendlyByteBuf, Any>

        return StreamCodec.of<FriendlyByteBuf, Set<*>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { element ->
                    elementCodec.encode(buf, element ?: error("Set字段不支持null元素: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                val set = LinkedHashSet<Any?>(size)
                repeat(size) {
                    set.add(elementCodec.decode(buf))
                }
                set
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun codecMap(type: Type): StreamCodec<out FriendlyByteBuf, *> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("Map字段必须声明具体泛型: $type")
        }

        val keyType = type.actualTypeArguments[0]
        val valueType = type.actualTypeArguments[1]
        val keyCodec = codecOf(keyType) as StreamCodec<FriendlyByteBuf, Any>
        val valueCodec = codecOf(valueType) as StreamCodec<FriendlyByteBuf, Any>

        return StreamCodec.of<FriendlyByteBuf, Map<*, *>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { (key, mapValue) ->
                    keyCodec.encode(buf, key ?: error("Map字段不支持null键: $type"))
                    valueCodec.encode(buf, mapValue ?: error("Map字段不支持null值: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                val map = LinkedHashMap<Any?, Any?>(size)
                repeat(size) {
                    map[keyCodec.decode(buf)] = valueCodec.decode(buf)
                }
                map
            }
        )
    }


    fun updateFields(current: Any, other: Any) {
        if (current::class.java != other::class.java) return
        val fields = current::class.java.declaredFields
        fields.filter {
            it.isAnnotationPresent(CodecField::class.java) &&
                    !Modifier.isFinal(it.modifiers) &&
                    !Modifier.isStatic(it.modifiers)
        }.forEach { field ->
            field.isAccessible = true
            field.set(current, field.get(other))
        }
    }


    fun isSupposedType(type: Class<*>) = supposedTypes[type.name] != null

    fun isSupposedType(type: String) = supposedTypes[type] != null

}
