package cn.coostack.cooparticlesapi.annotations.codec

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ForgeCodecHelper {
    val supposedTypes = ConcurrentHashMap<String, CommonStreamCodec<*>>()
    private val registryRequiredTypes = ConcurrentHashMap.newKeySet<String>()

    init {
        register(Short::class.java, CommonStreamCodec.of({ buf, i -> buf.writeShort(i.toInt()) }, { it.readShort() }))
        register(Int::class.java, CommonStreamCodec.of({ buf, i -> buf.writeInt(i) }, { it.readInt() }))
        register(Long::class.java, CommonStreamCodec.of({ buf, i -> buf.writeLong(i) }, { it.readLong() }))
        register(LongArray::class.java, CommonStreamCodec.of({ buf, i -> buf.writeLongArray(i) }, { it.readLongArray() }))
        register(Float::class.java, CommonStreamCodec.of({ buf, i -> buf.writeFloat(i) }, { it.readFloat() }))
        register(Double::class.java, CommonStreamCodec.of({ buf, i -> buf.writeDouble(i) }, { it.readDouble() }))
        register(String::class.java, CommonStreamCodec.of({ buf, i -> buf.writeUtf(i) }, { it.readUtf() }))
        register(Byte::class.java, CommonStreamCodec.of({ buf, i -> buf.writeByte(i.toInt()) }, { it.readByte() }))
        register(Boolean::class.java, CommonStreamCodec.of({ buf, i -> buf.writeBoolean(i) }, { it.readBoolean() }))
        register(ByteArray::class.java, CommonStreamCodec.of({ buf, i -> buf.writeByteArray(i) }, { it.readByteArray() }))
        register(CooUniformValue::class.java, CooUniformValue.STREAM_CODEC)
        register(Char::class.java, CommonStreamCodec.of({ buf, i -> buf.writeChar(i.code) }, { it.readChar() }))
        register(UUID::class.java, CommonStreamCodec.of({ buf, i -> buf.writeUUID(i) }, { it.readUUID() }))
        register(
            BlockPos::class.java,
            CommonStreamCodec.of({ buf, pos -> buf.writeBlockPos(pos) }, { buf -> buf.readBlockPos() })
        )
        register(
            BlockState::class.java,
            CommonStreamCodec.of({ buf, state ->
                val id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_STATE_REGISTRY.getId(state)
                buf.writeVarInt(id)
            }, { buf ->
                val id = buf.readVarInt()
                net.minecraft.core.registries.BuiltInRegistries.BLOCK_STATE_REGISTRY.byId(id)
                    ?: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
            })
        )
        register(Vector3f::class.java, CommonStreamCodec.of({ buf, i -> buf.writeVector3f(i) }, { it.readVector3f() }))
        register(Vector4f::class.java, CommonStreamCodec.of({ buf, v ->
            buf.writeFloat(v.x)
            buf.writeFloat(v.y)
            buf.writeFloat(v.z)
            buf.writeFloat(v.w)
        }, {
            Vector4f(it.readFloat(), it.readFloat(), it.readFloat(), it.readFloat())
        }))
        register(Vec2::class.java, CommonStreamCodec.of({ buf, i ->
            buf.writeFloat(i.x)
            buf.writeFloat(i.y)
        }, {
            Vec2(it.readFloat(), it.readFloat())
        }))
        register(Vec3::class.java, CommonStreamCodec.of({ buf, i -> buf.writeVec3(i) }, { it.readVec3() }))
        register(Quaternionf::class.java, CommonStreamCodec.of({ buf, q -> buf.writeQuaternion(q) }, { it.readQuaternion() }))
        register(AABB::class.java, CommonStreamCodec.of({ buf, i ->
            buf.writeDouble(i.minX)
            buf.writeDouble(i.minY)
            buf.writeDouble(i.minZ)
            buf.writeDouble(i.maxX)
            buf.writeDouble(i.maxY)
            buf.writeDouble(i.maxZ)
        }, {
            AABB(it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble(), it.readDouble())
        }))
        register(ItemStack::class.java, CommonStreamCodec.of({ buf, i -> buf.writeItem(i) }, { it.readItem() }))
    }

    @JvmStatic
    fun <T> register(type: Class<T>, codec: CommonStreamCodec<T>) {
        supposedTypes[type.name] = codec
        registryRequiredTypes.remove(type.name)
    }

    @JvmStatic
    fun <T> registerRegistry(type: Class<T>, codec: CommonStreamCodec<T>) {
        supposedTypes[type.name] = codec
        registryRequiredTypes.add(type.name)
    }

    fun codecOf(type: Type): CommonStreamCodec<*> {
        val codecType = normalizeCodecType(type)
        if (codecType is Class<*>) {
            require(codecType.name !in registryRequiredTypes) {
                "Type ${codecType.name} requires registry-aware codec; use registryCodecOf"
            }
            return supposedTypes[codecType.name]
                ?: throw IllegalArgumentException("Unsupported type: ${codecType.name}")
        }
        if (codecType is ParameterizedType) {
            val raw = codecType.rawType as Class<*>
            if (List::class.java.isAssignableFrom(raw)) return codecList(codecType)
            if (Set::class.java.isAssignableFrom(raw)) return codecSet(codecType)
            if (Map::class.java.isAssignableFrom(raw)) return codecMap(codecType)
        }
        throw IllegalArgumentException("Unsupported field type: $type")
    }

    @Suppress("UNCHECKED_CAST")
    fun registryCodecOf(type: Type): CommonStreamCodec<*> {
        val codecType = normalizeCodecType(type)
        if (codecType is Class<*>) {
            return supposedTypes[codecType.name] as? CommonStreamCodec<*>
                ?: throw IllegalArgumentException("Unsupported type: ${codecType.name}")
        }
        if (codecType is ParameterizedType) {
            val raw = codecType.rawType as Class<*>
            if (List::class.java.isAssignableFrom(raw)) return registryCodecList(codecType)
            if (Set::class.java.isAssignableFrom(raw)) return registryCodecSet(codecType)
            if (Map::class.java.isAssignableFrom(raw)) return registryCodecMap(codecType)
        }
        throw IllegalArgumentException("Unsupported field type: $type")
    }

    private fun normalizeCodecType(type: Type): Type {
        if (type is WildcardType) {
            if (type.lowerBounds.isNotEmpty()) {
                throw IllegalArgumentException("Unsupported field type: $type")
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
    fun codecList(type: Type): CommonStreamCodec<*> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("List field must declare generic type: $type")
        }
        val elementType = type.actualTypeArguments[0]
        val elementCodec = codecOf(elementType)
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { element ->
                elementCodec.encode(buf, element ?: error("List field does not support null elements: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            val list = ArrayList<Any?>(size)
            repeat(size) {
                list.add(elementCodec.decode(buf))
            }
            list
        })
    }

    @Suppress("UNCHECKED_CAST")
    fun codecSet(type: Type): CommonStreamCodec<*> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("Set field must declare generic type: $type")
        }
        val elementType = type.actualTypeArguments[0]
        val elementCodec = codecOf(elementType)
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { element ->
                elementCodec.encode(buf, element ?: error("Set field does not support null elements: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            val set = LinkedHashSet<Any?>(size)
            repeat(size) {
                set.add(elementCodec.decode(buf))
            }
            set
        })
    }

    @Suppress("UNCHECKED_CAST")
    fun codecMap(type: Type): CommonStreamCodec<*> {
        if (type !is ParameterizedType) {
            throw IllegalArgumentException("Map field must declare generic type: $type")
        }
        val keyType = type.actualTypeArguments[0]
        val valueType = type.actualTypeArguments[1]
        val keyCodec = codecOf(keyType)
        val valueCodec = codecOf(valueType)
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { (key, mapValue) ->
                keyCodec.encode(buf, key ?: error("Map field does not support null keys: $type"))
                valueCodec.encode(buf, mapValue ?: error("Map field does not support null values: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            val map = LinkedHashMap<Any?, Any?>(size)
            repeat(size) {
                map[keyCodec.decode(buf)] = valueCodec.decode(buf)
            }
            map
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registryCodecList(type: ParameterizedType): CommonStreamCodec<*> {
        val elementCodec = registryCodecOf(type.actualTypeArguments[0])
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { element ->
                elementCodec.encode(buf, element ?: error("List field does not support null elements: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            List(size) { elementCodec.decode(buf) }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registryCodecSet(type: ParameterizedType): CommonStreamCodec<*> {
        val elementCodec = registryCodecOf(type.actualTypeArguments[0])
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { element ->
                elementCodec.encode(buf, element ?: error("Set field does not support null elements: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            LinkedHashSet<Any>(size).apply {
                repeat(size) { add(elementCodec.decode(buf)) }
            }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registryCodecMap(type: ParameterizedType): CommonStreamCodec<*> {
        val keyCodec = registryCodecOf(type.actualTypeArguments[0])
        val valueCodec = registryCodecOf(type.actualTypeArguments[1])
        return CommonStreamCodec.of({ buf, value ->
            buf.writeVarInt(value.size)
            value.forEach { (key, mapValue) ->
                keyCodec.encode(buf, key ?: error("Map field does not support null keys: $type"))
                valueCodec.encode(buf, mapValue ?: error("Map field does not support null values: $type"))
            }
        }, { buf ->
            val size = buf.readVarInt()
            LinkedHashMap<Any, Any>(size).apply {
                repeat(size) { put(keyCodec.decode(buf), valueCodec.decode(buf)) }
            }
        })
    }

    fun updateFields(current: Any, other: Any) {
        if (current::class.java != other::class.java) return
        CodecFieldAccessor.fields(current::class.java).forEach { field ->
            CodecFieldAccessor.set(field, current, CodecFieldAccessor.get(field, other))
        }
    }

    fun isSupposedType(type: Class<*>) = supposedTypes[type.name] != null
    fun isSupposedType(type: String) = supposedTypes[type] != null

    @JvmStatic
    fun particleCodecOf(particle: ParticleOptions): CommonStreamCodec<ParticleOptions> {
        return CommonStreamCodec.of({ buf, p ->
            val id = BuiltInRegistries.PARTICLE_TYPE.getKey(p.type)
            buf.writeResourceLocation(id)
            if (p is cn.coostack.cooparticlesapi.particles.ControlableParticleEffect) {
                val codec = p.getPacketCodec()
                codec.encode(buf, p)
            } else {
                p.writeToPacket(buf)
            }
        }, { buf ->
            val id = buf.readResourceLocation()
            val type = BuiltInRegistries.PARTICLE_TYPE.get(id)
                ?: error("Unknown particle type: $id")
            @Suppress("UNCHECKED_CAST")
            val particleType = type as ParticleType<ParticleOptions>
            val result = particleType.codec.parse(
                BuiltInRegistries.PARTICLE_TYPE.asSerializerId(),
                net.minecraft.server.packs.resources.ResourceManager.Empty()
            )
            result.orElseThrow()
        })
    }
}
