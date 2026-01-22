package cn.coostack.cooparticlesapi.annotations.codec

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.SimpleRandomParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.data.DoubleRangeData
import cn.coostack.cooparticlesapi.network.particle.emitters.data.FloatRangeData
import cn.coostack.cooparticlesapi.network.particle.emitters.data.IntRangeData
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorDouble
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorFloat
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorVec3d
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorVector3f
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.data.InterpolatorRelativeLocation
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CodecHelper {
    val supposedTypes = ConcurrentHashMap<String, StreamCodec<out FriendlyByteBuf, *>>()

    init {
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
        register(Char::class.java, StreamCodec.of({ buf, i -> buf.writeChar(i.toInt()) }, { it.readChar() }))
        register(UUID::class.java, StreamCodec.of({ buf, i -> buf.writeUUID(i) }, { it.readUUID() }))
        register(ControlableParticleData::class.java, ControlableParticleData.PACKET_CODEC)
        register(Vector3f::class.java, StreamCodec.of({ buf, i -> buf.writeVector3f(i) }, { it.readVector3f() }))
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
        register(ItemStack::class.java, ItemStack.STREAM_CODEC)
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

    fun updateFields(current: Any, other: Any) {
        if (current::class.java != other::class.java) return
        val fields = current::class.java.declaredFields
        fields.filter {
            it.isAnnotationPresent(CodecField::class.java) && !Modifier.isFinal(it.modifiers)
        }.forEach { field ->
            field.isAccessible = true
            field.set(current, field.get(other))
        }
    }


}