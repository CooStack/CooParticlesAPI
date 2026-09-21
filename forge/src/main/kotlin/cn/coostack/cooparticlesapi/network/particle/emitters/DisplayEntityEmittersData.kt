package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.display.DisplayEntity
import net.minecraft.network.PacketByteBuf

open class DisplayEntityEmittersData : ControlableParticleData() {
    companion object {
        val CODEC: ForgeStreamCodec<PacketByteBuf, DisplayEntityEmittersData> = ForgeStreamCodec.of(
            { buf, data ->
                ControlableParticleData.PACKET_CODEC.encode(buf, data)
            },
            { buf ->
                val data = ControlableParticleData.PACKET_CODEC.decode(buf) as ControlableParticleData
                DisplayEntityEmittersData().also {
                    it.uuid = data.uuid
                    it.velocity = data.velocity
                    it.weightSize = data.weightSize
                    it.heightSize = data.heightSize
                    it.uniformSize = data.uniformSize
                    it.visibleRange = data.visibleRange
                    it.color = Vector3f(data.color)
                    it.alpha = data.alpha
                    it.age = data.age
                    it.maxAge = data.maxAge
                    it.textureSheet = data.textureSheet
                    it.effect = data.effect
                    it.speed = data.speed
                    it.speedLimit = data.speedLimit
                    it.sign = data.sign
                    it.light = data.light
                    it.cameraOption = data.cameraOption
                    it.axis = data.axis
                    it.yaw = data.yaw
                    it.pitch = data.pitch
                    it.roll = data.roll
                    it.depthSize = data.depthSize
                }
            }
        )
    }

    var displayEntity: DisplayEntity? = null

    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, out SerializableData> {
        return CODEC
    }

    override fun clone(): SerializableData {
        return super.clone().also {
            val data = it as DisplayEntityEmittersData
            data.displayEntity = displayEntity
        }
    }
}
