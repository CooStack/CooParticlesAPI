package cn.coostack.cooparticlesapi.particles

import com.mojang.serialization.Codec
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

enum class ParticleCameraOption(
    val enableAxis: Boolean,
    val enableYaw: Boolean,
    val enablePitch: Boolean,
    val enableRoll: Boolean
) {
    BILLBOARD(
        enableAxis = false,
        enableYaw = false,
        enablePitch = false,
        enableRoll = true
    ),
    AXIS_BILLBOARD(
        enableAxis = true,
        enableYaw = false,
        enablePitch = false,
        enableRoll = true
    ),
    ROTATION(
        enableAxis = false,
        enableYaw = true,
        enablePitch = true,
        enableRoll = true
    );

    companion object {
        @JvmStatic
        val CODEC: Codec<ParticleCameraOption> = Codec.STRING.xmap(
            { value -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BILLBOARD },
            { value -> value.name.lowercase() }
        )

        @JvmStatic
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ParticleCameraOption> = StreamCodec.of(
            { buf, value -> buf.writeEnum(value) },
            { buf -> buf.readEnum(ParticleCameraOption::class.java) }
        )

        @JvmStatic
        fun fromFaceToCamera(faceToCamera: Boolean): ParticleCameraOption {
            return if (faceToCamera) BILLBOARD else ROTATION
        }
    }
}
