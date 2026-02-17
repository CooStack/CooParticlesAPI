package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.particles.control.ControlType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * 三个参数
 * @param uuid 更改的粒子组的UUID
 * @param type 三种类型对应三种不同的包参数
 *      1. REMOVE args什么都不写
 *      2. CREATE args输入
 *          1. pos: Vec3d
 *          2. groupType: Class<ControlableParticleGroup>
 *          3. currentTick: Int
 *          4. maxTick: Int
 *          5. ByteBuf 用于ControlableParticleGroup 构建的输入参数(不包括自身的UUID)
 *          6. scale 缩放大小
 *      3. CHANGE args输入支持
 *          1. pos: Vec3d
 *          2. rotateTo: Vec3d
 *          3. rotateAxis: Double / Float
 *          4. axis: Vec3d
 *          5. currentTick: Int
 *          6. maxTick: Int
 *          7. invoke: 无参方法名
 *          8. scale 缩放大小
 */
class PacketParticleGroupS2C(
    val uuid: UUID,
    val type: ControlType,
    val args: Map<String, ParticleControlerDataBuffer<*>>
) : CustomPacketPayload {
    enum class PacketArgsType(val ofArgs: String) {
        POS("pos"),
        CURRENT_TICK("current_tick"),
        MAX_TICK("max_tick"),
        ROTATE_TO("rotate_to"),
        ROTATE_AXIS("rotate_axis"),
        INVOKE("invoke"),
        AXIS("axis"),
        SCALE("scale"),
        GROUP_TYPE("groupType");

        companion object {
            fun fromArgsName(value: String): PacketArgsType {
                return when (value) {
                    "pos" -> return POS
                    "current_tick" -> return CURRENT_TICK
                    "max_tick" -> return MAX_TICK
                    "rotate_to" -> return ROTATE_TO
                    "rotate_axis" -> return ROTATE_AXIS
                    "invoke" -> return INVOKE
                    "scale" -> return SCALE
                    "groupType" -> return GROUP_TYPE
                    else -> INVOKE
                }
            }
        }
    }

    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "particle_group")
        val payloadID = CustomPacketPayload.Type<PacketParticleGroupS2C>(identifierID)
        val CODEC: StreamCodec<FriendlyByteBuf, PacketParticleGroupS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeUUID(packet.uuid)
                buf.writeInt(packet.type.id)
                packet.args.forEach { (t, u) ->
                    val encode = ParticleControlerDataBuffers.encode(u)
                    val len = encode.size
                    buf.writeInt(len)
                    buf.writeUtf(t)
                    buf.writeBytes(encode)
                }
            }, { buf ->
                val args = HashMap<String, ParticleControlerDataBuffer<*>>()
                val uuid = buf.readUUID()
                val id = buf.readInt()
                val type = ControlType.Companion.getTypeById(id)
                while (buf.readableBytes() != 0) {
                    val len = buf.readInt()
                    val key = buf.readUtf()
                    val value = ByteArray(len)
                    buf.readBytes(value)
                    val decode = ParticleControlerDataBuffers.decodeToBuffer<Any>(value)
                    args[key] = decode
                }
                return@codec PacketParticleGroupS2C(
                    uuid, type, args
                )
            }
            )

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
        return payloadID
    }
}