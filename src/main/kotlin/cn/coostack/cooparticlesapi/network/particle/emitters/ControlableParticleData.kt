package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticleAPI
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffectManager
import cn.coostack.cooparticlesapi.particles.impl.TestEndRodEffect
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f
import java.util.UUID

open class ControlableParticleData {
    companion object {
        @JvmStatic
        val PACKET_CODEC = PacketCodec.ofStatic<PacketByteBuf, ControlableParticleData>(
            ::encode, ::decode
        )

        private fun encode(buf: PacketByteBuf, data: ControlableParticleData) {
            buf.writeUuid(data.uuid)
            buf.writeVec3d(data.velocity)
            buf.writeFloat(data.size)
            buf.writeFloat(data.visibleRange)
            buf.writeVector3f(data.color)
            buf.writeFloat(data.alpha)
            buf.writeInt(data.age)
            buf.writeInt(data.maxAge)
            buf.writeString(data.textureSheet.toString())
            buf.writeString(data.effect::class.java.name)
            buf.writeUuid(data.uuid)
            buf.writeDouble(data.speed)
        }

        private fun decode(
            buf: PacketByteBuf,
        ): ControlableParticleData {
            val uuid = buf.readUuid()
            val velocity = buf.readVec3d()
            val size = buf.readFloat()
            val visibleRange = buf.readFloat()
            val color = buf.readVector3f()
            val alpha = buf.readFloat()
            val age = buf.readInt()
            val maxAge = buf.readInt()
            val textureSheet = buf.readString()
            val effectType = buf.readString()
            val effectUUID = buf.readUuid()
            val effect = ControlableParticleEffectManager.createWithUUID(
                effectUUID,
                Class.forName(effectType) as Class<ControlableParticleEffect>
            )
            val speed = buf.readDouble()
//            val effect = ParticleTypes.PACKET_CODEC.decode(buf) as ControlableParticleEffect
            return ControlableParticleData().apply {
                this.uuid = uuid
                this.velocity = velocity
                this.color = color
                this.alpha = alpha
                this.size = size
                this.visibleRange = visibleRange
                this.age = age
                this.maxAge = maxAge
                this.textureSheet = textureSheet
                this.effect = effect
                this.speed = speed
            }
        }
    }

    var uuid = UUID.randomUUID()
    var velocity: Vec3d = Vec3d.ZERO
    var size = 0.2f
    var color = Vector3f(1f, 1f, 1f)
    var alpha = 1f
    var age = 0
    var maxAge = 120
    var visibleRange = 128f
    var effect: ControlableParticleEffect = TestEndRodEffect(uuid)

    // 脑瘫东西设置了客户端专属
    private var textureSheet: String = "PARTICLE_SHEET_TRANSLUCENT"

    /**
     * 粒子移动速度
     */
    var speed: Double = 1.0
    fun textureSheetFromString(sheet: String): ParticleTextureSheet? {
        return when (sheet) {
            ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT.toString() -> ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
            ParticleTextureSheet.PARTICLE_SHEET_OPAQUE.toString() -> ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
            ParticleTextureSheet.CUSTOM.toString() -> ParticleTextureSheet.CUSTOM
            ParticleTextureSheet.NO_RENDER.toString() -> ParticleTextureSheet.NO_RENDER
            ParticleTextureSheet.PARTICLE_SHEET_LIT.toString() -> ParticleTextureSheet.PARTICLE_SHEET_LIT
            ParticleTextureSheet.TERRAIN_SHEET.toString() -> ParticleTextureSheet.TERRAIN_SHEET
            else -> null
        }
    }

    fun getTextureSheet(): ParticleTextureSheet {
        return textureSheetFromString(textureSheet) ?: let {
            CooParticleAPI.logger.error("can not find textureSheet $textureSheet")
            ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
        }
    }

    fun setTextureSheet(value: String) {
        this.textureSheet = value
    }

    fun setTextureSheet(value: ParticleTextureSheet) {
        this.textureSheet = value.toString()
    }

    open fun getCodec(): PacketCodec<PacketByteBuf, out ControlableParticleData> {
        return PACKET_CODEC
    }


    open fun clone(): ControlableParticleData {
        return ControlableParticleData().also {
            it.uuid = UUID.randomUUID()
            it.velocity = this.velocity
            it.size = this.size
            it.color = this.color
            it.alpha = this.alpha
            it.visibleRange = this.visibleRange
            it.effect = this.effect
            it.age = this.age
            it.maxAge = this.maxAge
            it.effect = this.effect.clone()
            it.textureSheet = this.textureSheet
            it.speed = this.speed
        }
    }
}