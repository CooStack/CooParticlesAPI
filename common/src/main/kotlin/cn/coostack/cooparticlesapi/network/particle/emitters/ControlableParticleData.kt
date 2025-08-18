package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffectManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID

open class ControlableParticleData {
    companion object {
        @JvmStatic
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, ControlableParticleData> =
            StreamCodec.of<FriendlyByteBuf, ControlableParticleData>(
                ::encode, ::decode
            )

        private fun encode(buf: FriendlyByteBuf, data: ControlableParticleData) {
            buf.writeUUID(data.uuid)
            buf.writeVec3(data.velocity)
            buf.writeFloat(data.size)
            buf.writeFloat(data.visibleRange)
            buf.writeVector3f(data.color)
            buf.writeFloat(data.alpha)
            buf.writeInt(data.age)
            buf.writeInt(data.maxAge)
            buf.writeUtf(data.textureSheet.toString())
            buf.writeUtf(data.effect::class.java.name)
            buf.writeUUID(data.uuid)
            buf.writeDouble(data.speed)
        }

        private fun decode(
            buf: FriendlyByteBuf,
        ): ControlableParticleData {
            val uuid = buf.readUUID()
            val velocity = buf.readVec3()
            val size = buf.readFloat()
            val visibleRange = buf.readFloat()
            val color = buf.readVector3f()
            val alpha = buf.readFloat()
            val age = buf.readInt()
            val maxAge = buf.readInt()
            val textureSheet = buf.readUtf()
            val effectType = buf.readUtf()
            val effectUUID = buf.readUUID()
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
    var velocity: Vec3 = Vec3.ZERO
    var size = 0.2f
    var color = Vector3f(1f, 1f, 1f)
    var alpha = 1f
    var age = 0
    var maxAge = 120
    var visibleRange = 128f
    var effect: ControlableParticleEffect = ControlableEndRodEffect(uuid)

    // 脑瘫东西设置了客户端专属
    private var textureSheet: String = "PARTICLE_SHEET_TRANSLUCENT"

    /**
     * 粒子移动速度
     */
    var speed: Double = 1.0
    fun textureSheetFromString(sheet: String): ParticleRenderType? {
        return when (sheet) {
            ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.toString() -> ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
            ParticleRenderType.PARTICLE_SHEET_OPAQUE.toString() -> ParticleRenderType.PARTICLE_SHEET_OPAQUE
            ParticleRenderType.CUSTOM.toString() -> ParticleRenderType.CUSTOM
            ParticleRenderType.NO_RENDER.toString() -> ParticleRenderType.NO_RENDER
            ParticleRenderType.PARTICLE_SHEET_LIT.toString() -> ParticleRenderType.PARTICLE_SHEET_LIT
            ParticleRenderType.TERRAIN_SHEET.toString() -> ParticleRenderType.TERRAIN_SHEET
            else -> null
        }
    }

    fun getTextureSheet(): ParticleRenderType {
        return textureSheetFromString(textureSheet) ?: let {
            CooParticlesConstants.logger.error("can not find textureSheet $textureSheet")
            ParticleRenderType.PARTICLE_SHEET_OPAQUE
        }
    }

    fun setTextureSheet(value: String) {
        this.textureSheet = value
    }

    fun setTextureSheet(value: ParticleRenderType) {
        this.textureSheet = value.toString()
    }

    open fun getCodec(): StreamCodec<FriendlyByteBuf, out ControlableParticleData> {
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