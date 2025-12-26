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
        val particleTexturesMapper: MutableMap<String, ParticleRenderType> = mutableMapOf()

        @JvmStatic
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, ControlableParticleData> =
            StreamCodec.of(
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
            buf.writeUtf(data.textureSheet)
            buf.writeUtf(data.effect::class.java.name)
            buf.writeUUID(data.uuid)
            buf.writeDouble(data.speed)
            buf.writeDouble(data.speedLimit)
            buf.writeInt(data.sign)
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
            val speedLimit = buf.readDouble()
            val sign = buf.readInt()
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
                this.sign = sign
                this.speedLimit = speedLimit
            }
        }

        @JvmStatic
        fun registerRenderType(type: ParticleRenderType) {
            particleTexturesMapper[type.toString()] = type
        }

        init {
            particleTexturesMapper[ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.toString()] =
                ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
            particleTexturesMapper[ParticleRenderType.PARTICLE_SHEET_OPAQUE.toString()] =
                ParticleRenderType.PARTICLE_SHEET_OPAQUE
            particleTexturesMapper[ParticleRenderType.CUSTOM.toString()] = ParticleRenderType.CUSTOM
            particleTexturesMapper[ParticleRenderType.NO_RENDER.toString()] = ParticleRenderType.NO_RENDER
            particleTexturesMapper[ParticleRenderType.PARTICLE_SHEET_LIT.toString()] =
                ParticleRenderType.PARTICLE_SHEET_LIT
            particleTexturesMapper[ParticleRenderType.TERRAIN_SHEET.toString()] = ParticleRenderType.TERRAIN_SHEET
        }

    }

    /**
     * 粒子生成时会传输的控制UUID
     */
    var uuid = UUID.randomUUID()

    /**
     * 粒子的移动向量
     * 在粒子发射器中 会不断调用这次的参数
     */
    var velocity: Vec3 = Vec3.ZERO
    var size = 0.2f

    /**
     * 粒子生成时采用的颜色，后续控制不生效
     */
    var color = Vector3f(1f, 1f, 1f)

    /**
     * 粒子生成时采用的不透明度
     */
    var alpha = 1f

    /**
     * 粒子生成时设置的age
     */
    var age = 0

    /**
     * 粒子最大生命周期
     */
    var maxAge = 120

    /**
     * 粒子可见范围
     */
    var visibleRange = 128f

    /**
     * 粒子样式 （必须是可控制的粒子）
     */
    var effect: ControlableParticleEffect = ControlableEndRodEffect(uuid)

    /**
     * 一些特殊标识
     * 用于在single 区分不同类型 分工的粒子
     */
    var sign = 0

    /**
     * 粒子移动速度上限
     * 防止不知道什么原因导致粒子移速过高从而导致客户端卡死
     */
    var speedLimit = 32.0

    // 脑瘫东西设置了客户端专属
    // 粒子渲染方式 只生效一次
    private var textureSheet: String = "PARTICLE_SHEET_TRANSLUCENT"

    /**
     * 粒子移动速度
     * 在ClassParticlesEmitters默认不生效
     */
    var speed: Double = 1.0
    fun textureSheetFromString(sheet: String): ParticleRenderType? {
        return particleTexturesMapper[sheet]
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