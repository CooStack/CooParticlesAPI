package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.codec.ForgeCodecHelper
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID

open class ControlableParticleData : SerializableData {
    companion object {
        @JvmStatic
        val particleTexturesMapper: MutableMap<String, ParticleRenderType> = mutableMapOf()

        @JvmStatic
        val PACKET_CODEC: ForgeStreamCodec<ControlableParticleData> =
            ForgeStreamCodec.of(
                ::encodeBase,
                { buf -> decodeBase(buf, ControlableParticleData()) },
            )

        internal fun encodeBase(buf: PacketByteBuf, data: ControlableParticleData) {
            buf.writeUUID(data.uuid)
            buf.writeVec3(data.velocity)
            buf.writeFloat(data.weightSize)
            buf.writeFloat(data.heightSize)
            buf.writeBoolean(data.uniformSize)
            buf.writeFloat(data.visibleRange)
            buf.writeVector3f(data.color)
            buf.writeFloat(data.alpha)
            buf.writeInt(data.age)
            buf.writeInt(data.maxAge)
            buf.writeUtf(data.textureSheet)

            ForgeCodecHelper.particleCodecOf(data.effect).encode(buf, data.effect)

            buf.writeDouble(data.speed)
            buf.writeDouble(data.speedLimit)
            buf.writeInt(data.sign)
            buf.writeInt(data.light)
            ParticleCameraOption.STREAM_CODEC.encode(buf, data.cameraOption)
            buf.writeVec3(data.axis)
            buf.writeFloat(data.yaw)
            buf.writeFloat(data.pitch)
            buf.writeFloat(data.roll)
            buf.writeFloat(data.depthSize)
        }

        internal fun decodeBase(buf: PacketByteBuf, data: ControlableParticleData): ControlableParticleData {
            data.uuid = buf.readUUID()
            data.velocity = buf.readVec3()
            data.weightSize = buf.readFloat()
            data.heightSize = buf.readFloat()
            data.uniformSize = buf.readBoolean()
            data.visibleRange = buf.readFloat()
            data.color = buf.readVector3f()
            data.alpha = buf.readFloat()
            data.age = buf.readInt()
            data.maxAge = buf.readInt()
            data.textureSheet = buf.readUtf()

            data.effect = ForgeCodecHelper.particleCodecOf(data.effect).decode(buf) as ControlableParticleEffect

            data.speed = buf.readDouble()
            data.speedLimit = buf.readDouble()
            data.sign = buf.readInt()
            data.light = buf.readInt()
            data.cameraOption = ParticleCameraOption.STREAM_CODEC.decode(buf)
            data.axis = buf.readVec3()
            data.yaw = buf.readFloat()
            data.pitch = buf.readFloat()
            data.roll = buf.readFloat()
            data.depthSize = buf.readFloat()
            return data
        }
    }

    var uuid: UUID = UUID.randomUUID()
    var velocity: Vec3 = Vec3.ZERO
    var weightSize: Float = 0.3f
    var heightSize: Float = 0.3f
    var uniformSize: Boolean = true
    var visibleRange: Float = 256f
    var color: Vector3f = Vector3f(1f, 1f, 1f)
    var alpha: Float = 1f
    var age: Int = 0
    var maxAge: Int = 20
    var textureSheet: String = ""
    var effect: ControlableParticleEffect = ControlableEndRodEffect.codec
    var speed: Double = 0.0
    var speedLimit: Double = 1.0
    var sign: Int = 0
    var light: Int = 15
    var cameraOption: ParticleCameraOption = ParticleCameraOption.BILLBOARD
    var axis: Vec3 = Vec3.ZERO
    var yaw: Float = 0f
    var pitch: Float = 0f
    var roll: Float = 0f
    var depthSize: Float = 0.3f

    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, out SerializableData> {
        return PACKET_CODEC
    }

    override fun clone(): SerializableData {
        return ControlableParticleData().also {
            it.uuid = uuid
            it.velocity = velocity
            it.weightSize = weightSize
            it.heightSize = heightSize
            it.uniformSize = uniformSize
            it.visibleRange = visibleRange
            it.color = Vector3f(color)
            it.alpha = alpha
            it.age = age
            it.maxAge = maxAge
            it.textureSheet = textureSheet
            it.effect = effect.clone()
            it.speed = speed
            it.speedLimit = speedLimit
            it.sign = sign
            it.light = light
            it.cameraOption = cameraOption
            it.axis = axis
            it.yaw = yaw
            it.pitch = pitch
            it.roll = roll
            it.depthSize = depthSize
        }
    }

    override fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*> {
        return ControlParticleManager.createControler(world, pos, this, particleLerpProcess, posLerpProcess)
    }

    override fun getDisplayer(): ParticleDisplayer {
        return ParticleDisplayer()
    }
}
