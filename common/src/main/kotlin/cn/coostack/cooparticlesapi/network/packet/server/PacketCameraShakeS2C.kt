package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class PacketCameraShakeS2C(
    val operation: CameraOperation,
    val range: Double,
    val origin: Vec3,
    val amplitude: Double,
    val tick: Int,
    val position: Vec3,
    val yawOffset: Float,
    val pitchOffset: Float,
    val instant: Boolean
) : CustomPacketPayload {
    constructor(range: Double, origin: Vec3, amplitude: Double, tick: Int) : this(
        CameraOperation.SHAKE,
        range,
        origin,
        amplitude,
        tick,
        Vec3.ZERO,
        0f,
        0f,
        false
    )

    enum class CameraOperation(val id: Int) {
        SHAKE(0),
        SET_OFFSET(1),
        RESET_OFFSET(2),
        FORCE_POSITION(3),
        RESET_FORCE_POSITION(4),
        RESET_ALL(5)
    }

    companion object {
        private val identifierID = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "camara_shake")
        val payloadID = CustomPacketPayload.Type<PacketCameraShakeS2C>(identifierID)

        val CODEC: StreamCodec<FriendlyByteBuf, PacketCameraShakeS2C> =
            CustomPacketPayload.codec({ packet, buf ->
                buf.writeByte(packet.operation.id)
                buf.writeDouble(packet.range)
                buf.writeVec3(packet.origin)
                buf.writeDouble(packet.amplitude)
                buf.writeInt(packet.tick)
                buf.writeVec3(packet.position)
                buf.writeFloat(packet.yawOffset)
                buf.writeFloat(packet.pitchOffset)
                buf.writeBoolean(packet.instant)
            }, { buf ->
                val operation = operationFromId(buf.readUnsignedByte().toInt())
                val range = buf.readDouble()
                val origin = buf.readVec3()
                val amplitude = buf.readDouble()
                val tick = buf.readInt()
                val position = buf.readVec3()
                val yawOffset = buf.readFloat()
                val pitchOffset = buf.readFloat()
                val instant = buf.readBoolean()
                PacketCameraShakeS2C(
                    operation,
                    range,
                    origin,
                    amplitude,
                    tick,
                    position,
                    yawOffset,
                    pitchOffset,
                    instant
                )
            })

        fun shake(range: Double, origin: Vec3, amplitude: Double, tick: Int): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(CameraOperation.SHAKE, range, origin, amplitude, tick, Vec3.ZERO, 0f, 0f, false)
        }

        fun setOffset(
            positionOffset: Vec3,
            yawOffset: Float = 0f,
            pitchOffset: Float = 0f,
            instant: Boolean = false
        ): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(
                CameraOperation.SET_OFFSET,
                -1.0,
                Vec3.ZERO,
                0.0,
                0,
                positionOffset,
                yawOffset,
                pitchOffset,
                instant
            )
        }

        fun resetOffset(instant: Boolean = false): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(
                CameraOperation.RESET_OFFSET,
                -1.0,
                Vec3.ZERO,
                0.0,
                0,
                Vec3.ZERO,
                0f,
                0f,
                instant
            )
        }

        fun forcePosition(position: Vec3, instant: Boolean = false): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(
                CameraOperation.FORCE_POSITION,
                -1.0,
                Vec3.ZERO,
                0.0,
                0,
                position,
                0f,
                0f,
                instant
            )
        }

        fun resetForcePosition(instant: Boolean = false): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(
                CameraOperation.RESET_FORCE_POSITION,
                -1.0,
                Vec3.ZERO,
                0.0,
                0,
                Vec3.ZERO,
                0f,
                0f,
                instant
            )
        }

        fun resetAll(instant: Boolean = false): PacketCameraShakeS2C {
            return PacketCameraShakeS2C(
                CameraOperation.RESET_ALL,
                -1.0,
                Vec3.ZERO,
                0.0,
                0,
                Vec3.ZERO,
                0f,
                0f,
                instant
            )
        }

        private fun operationFromId(id: Int): CameraOperation {
            return CameraOperation.values().firstOrNull { it.id == id } ?: CameraOperation.SHAKE
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return payloadID
    }
}

