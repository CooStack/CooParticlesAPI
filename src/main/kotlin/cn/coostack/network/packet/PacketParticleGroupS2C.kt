package cn.coostack.network.packet

import cn.coostack.CooParticleAPI
import cn.coostack.network.buffer.ParticleControlerDataBuffer
import cn.coostack.network.buffer.ParticleControlerDataBuffers
import cn.coostack.particles.control.ControlType
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
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
 *      3. CHANGE args输入支持
 *          1. pos: Vec3d
 *          2. rotateTo: Vec3d
 *          3. rotateAxis: Double / Float
 *          4. axis: Vec3d
 *          5. currentTick: Int
 *          6. maxTick: Int
 *          7. invoke: 无参方法名
 */
class PacketParticleGroupS2C(
    val uuid: UUID,
    val type: ControlType,
    val args: Map<String, ParticleControlerDataBuffer<*>>
) : CustomPayload {
    enum class PacketArgsType(val toArgsName: String) {
        POS("pos"),
        CURRENT_TICK("current_tick"),
        MAX_TICK("max_tick"),
        ROTATE_TO("rotate_to"),
        ROTATE_AXIS("rotate_axis"),
        INVOKE("invoke"),
        AXIS("axis"),
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
                    "groupType" -> return GROUP_TYPE
                    else -> INVOKE
                }
            }
        }
    }

    companion object {
        private val identifierID = Identifier.of(CooParticleAPI.MOD_ID, "particle_group")
        val payloadID = CustomPayload.Id<PacketParticleGroupS2C>(identifierID)
        private val CODEC: PacketCodec<PacketByteBuf, PacketParticleGroupS2C> =
            CustomPayload.codecOf({ packet, buf ->
                buf.writeUuid(packet.uuid)
                buf.writeInt(packet.type.id)
                packet.args.forEach { (t, u) ->
                    val encode = ParticleControlerDataBuffers.encode(u)
                    val len = encode.size
                    buf.writeInt(len)
                    buf.writeString(t)
                    buf.writeBytes(encode)
                }
            }, { buf ->
                val args = HashMap<String, ParticleControlerDataBuffer<*>>()
                val uuid = buf.readUuid()
                val id = buf.readInt()
                val type = ControlType.getTypeById(id)
                while (buf.readableBytes() != 0) {
                    val len = buf.readInt()
                    val key = buf.readString()
                    val value = ByteArray(len)
                    buf.readBytes(value)
                    val decode = ParticleControlerDataBuffers.decodeToBuffer<Any>(value)
                    args[key] = decode
                }
                return@codecOf PacketParticleGroupS2C(
                    uuid, type, args
                )
            }
            )

        fun init() {
            PayloadTypeRegistry.playS2C().register(payloadID, CODEC)
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> {
        return payloadID
    }
}