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
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.CompositionEmittersData
import cn.coostack.cooparticlesapi.network.particle.emitters.DisplayEntityEmittersData
import cn.coostack.cooparticlesapi.network.particle.emitters.SimpleRandomParticleData
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
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
import net.minecraft.network.RegistryFriendlyByteBuf
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
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CodecHelper {
    val supposedTypes = ConcurrentHashMap<String, StreamCodec<out FriendlyByteBuf, *>>()

    /**
     * 记录只能由 [RegistryFriendlyByteBuf] 驱动的 codec 类型。
     *
     * Example: emitter 的 `ControlableCParticleData` 字段会通过 [registryCodecOf] 查询。
     * Forbidden: 普通 packet 或 RenderEntity codec 不能把这些类型当成 [FriendlyByteBuf] codec。
     */
    private val registryRequiredTypes = ConcurrentHashMap.newKeySet<String>()

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
        register(CooUniformValue::class.java, CooUniformValue.STREAM_CODEC)
        register(Char::class.java, StreamCodec.of({ buf, i -> buf.writeChar(i.code) }, { it.readChar() }))
        register(UUID::class.java, StreamCodec.of({ buf, i -> buf.writeUUID(i) }, { it.readUUID() }))
        registerRegistry(ControlableParticleData::class.java, ControlableParticleData.PACKET_CODEC)
        registerRegistry(ControlableCParticleData::class.java, ControlableCParticleData.PACKET_CODEC)
        registerRegistry(CParticleTextureSource::class.java, CParticleTextureSource.STREAM_CODEC)
        register(CParticleCurve::class.java, CParticleCurve.STREAM_CODEC)
        register(CParticleColorCurve::class.java, CParticleColorCurve.STREAM_CODEC)
        register(
            CParticleUpdateMode::class.java,
            StreamCodec.of(
                { buf, mode -> buf.writeByte(mode.ordinal) },
                { buf ->
                    val ordinal = buf.readUnsignedByte().toInt()
                    require(ordinal < CParticleUpdateMode.entries.size) {
                        "unknown CParticle update mode: $ordinal"
                    }
                    CParticleUpdateMode.entries[ordinal]
                },
            ),
        )
        registerRegistry(CompositionEmittersData::class.java, CompositionEmittersData.PACKET_CODEC)
        registerRegistry(DisplayEntityEmittersData::class.java, DisplayEntityEmittersData.PACKET_CODEC)
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
        registerRegistry(ItemStack::class.java, ItemStack.OPTIONAL_STREAM_CODEC)
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
    @JvmStatic
    fun <T> register(type: Class<T>, codec: StreamCodec<out FriendlyByteBuf, T>) {
        supposedTypes[type.name] = codec
        registryRequiredTypes.remove(type.name)
    }

    /**
     * 注册依赖注册表上下文的字段 codec。
     *
     * Example: `ControlableCParticleData.PACKET_CODEC` 由 emitter 自动 codec 使用。
     * Forbidden: 不要把只调用基础 `writeInt` 等操作的普通 codec 注册到这里。
     *
     * @param T 要编码的类型
     * @param type 类型对应的类
     * @param codec 需要 [RegistryFriendlyByteBuf] 的 codec
     */
    @JvmStatic
    fun <T> registerRegistry(
        type: Class<T>,
        codec: StreamCodec<RegistryFriendlyByteBuf, T>,
    ) {
        supposedTypes[type.name] = codec
        registryRequiredTypes.add(type.name)
    }

    /**
     * 转换为该list 基于该泛型的codec
     *
     * @param type
     */

    fun codecOf(type: Type): StreamCodec<out FriendlyByteBuf, *> {
        val codecType = normalizeCodecType(type)

        if (codecType is Class<*>) {
            require(codecType.name !in registryRequiredTypes) {
                "类型 ${codecType.name} 需要 RegistryFriendlyByteBuf；请使用 registryCodecOf"
            }
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

    /**
     * 返回可在注册表网络上下文中使用的字段 codec。
     *
     * 普通 [FriendlyByteBuf] codec 也可安全用于其子类 [RegistryFriendlyByteBuf]；集合会递归保持该约束。
     * Example: emitter 的 `@CodecField var template = ControlableCParticleData()` 使用本入口。
     * Forbidden: 调用方不能把返回值降级后传入普通 [FriendlyByteBuf]。
     *
     * @param type 字段的反射类型
     * @return 接受 [RegistryFriendlyByteBuf] 的字段 codec
     */
    @Suppress("UNCHECKED_CAST")
    fun registryCodecOf(type: Type): StreamCodec<RegistryFriendlyByteBuf, *> {
        val codecType = normalizeCodecType(type)

        if (codecType is Class<*>) {
            return supposedTypes[codecType.name] as? StreamCodec<RegistryFriendlyByteBuf, *>
                ?: throw IllegalArgumentException("不支持的类型: ${codecType.name}")
        }

        if (codecType is ParameterizedType) {
            val raw = codecType.rawType as Class<*>
            if (List::class.java.isAssignableFrom(raw)) return registryCodecList(codecType)
            if (Set::class.java.isAssignableFrom(raw)) return registryCodecSet(codecType)
            if (Map::class.java.isAssignableFrom(raw)) return registryCodecMap(codecType)
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
        if (type is Class<*>) {
            return when (type) {
                java.lang.Short::class.java -> Short::class.java
                java.lang.Integer::class.java -> Int::class.java
                java.lang.Long::class.java -> Long::class.java
                java.lang.Float::class.java -> Float::class.java
                java.lang.Double::class.java -> Double::class.java
                java.lang.Byte::class.java -> Byte::class.java
                java.lang.Boolean::class.java -> Boolean::class.java
                java.lang.Character::class.java -> Char::class.java
                else -> type
            }
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

    /**
     * 创建 registry-aware 的 List 字段 codec。
     *
     * Example: emitter 可声明 `List<ControlableCParticleData>`。
     * Forbidden: List 必须声明具体元素类型，且不支持 `null` 元素。
     *
     * @param type 带具体元素类型的 List 反射类型
     * @return registry-aware List codec
     */
    @Suppress("UNCHECKED_CAST")
    private fun registryCodecList(type: ParameterizedType): StreamCodec<RegistryFriendlyByteBuf, *> {
        val elementCodec = registryCodecOf(type.actualTypeArguments[0]) as
                StreamCodec<RegistryFriendlyByteBuf, Any>
        return StreamCodec.of<RegistryFriendlyByteBuf, List<*>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { element ->
                    elementCodec.encode(buf, element ?: error("List字段不支持null元素: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                List(size) { elementCodec.decode(buf) }
            },
        )
    }

    /**
     * 创建 registry-aware 的 Set 字段 codec。
     *
     * Example: emitter 可声明 `Set<CParticleTextureSource>`。
     * Forbidden: Set 必须声明具体元素类型，且不支持 `null` 元素。
     *
     * @param type 带具体元素类型的 Set 反射类型
     * @return registry-aware Set codec
     */
    @Suppress("UNCHECKED_CAST")
    private fun registryCodecSet(type: ParameterizedType): StreamCodec<RegistryFriendlyByteBuf, *> {
        val elementCodec = registryCodecOf(type.actualTypeArguments[0]) as
                StreamCodec<RegistryFriendlyByteBuf, Any>
        return StreamCodec.of<RegistryFriendlyByteBuf, Set<*>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { element ->
                    elementCodec.encode(buf, element ?: error("Set字段不支持null元素: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                LinkedHashSet<Any>(size).apply {
                    repeat(size) { add(elementCodec.decode(buf)) }
                }
            },
        )
    }

    /**
     * 创建 registry-aware 的 Map 字段 codec。
     *
     * Example: emitter 可声明 `Map<String, CParticleTextureSource>`。
     * Forbidden: Map 必须声明具体键值类型，且不支持 `null` 键或值。
     *
     * @param type 带具体键值类型的 Map 反射类型
     * @return registry-aware Map codec
     */
    @Suppress("UNCHECKED_CAST")
    private fun registryCodecMap(type: ParameterizedType): StreamCodec<RegistryFriendlyByteBuf, *> {
        val keyCodec = registryCodecOf(type.actualTypeArguments[0]) as
                StreamCodec<RegistryFriendlyByteBuf, Any>
        val valueCodec = registryCodecOf(type.actualTypeArguments[1]) as
                StreamCodec<RegistryFriendlyByteBuf, Any>
        return StreamCodec.of<RegistryFriendlyByteBuf, Map<*, *>>(
            { buf, value ->
                buf.writeVarInt(value.size)
                value.forEach { (key, mapValue) ->
                    keyCodec.encode(buf, key ?: error("Map字段不支持null键: $type"))
                    valueCodec.encode(buf, mapValue ?: error("Map字段不支持null值: $type"))
                }
            },
            { buf ->
                val size = buf.readVarInt()
                LinkedHashMap<Any, Any>(size).apply {
                    repeat(size) { put(keyCodec.decode(buf), valueCodec.decode(buf)) }
                }
            },
        )
    }


    fun updateFields(current: Any, other: Any) {
        if (current::class.java != other::class.java) return
        CodecFieldAccessor.fields(current::class.java).forEach { field ->
            CodecFieldAccessor.set(field, current, CodecFieldAccessor.get(field, other))
        }
    }


    fun isSupposedType(type: Class<*>) = supposedTypes[type.name] != null

    fun isSupposedType(type: String) = supposedTypes[type] != null

}
