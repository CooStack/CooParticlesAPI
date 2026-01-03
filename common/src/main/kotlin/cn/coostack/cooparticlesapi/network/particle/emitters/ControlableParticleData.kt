package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffect
import cn.coostack.cooparticlesapi.particles.ControlableParticleEffectManager
import cn.coostack.cooparticlesapi.particles.impl.ControlableEndRodEffect
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
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
            buf.writeInt(data.light)
            buf.writeBoolean(data.faceToCamera)
            buf.writeFloat(data.yaw)
            buf.writeFloat(data.pitch)
            buf.writeFloat(data.roll)
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
            val light = buf.readInt()
            val faceToCamera = buf.readBoolean()
            val yaw = buf.readFloat()
            val pitch = buf.readFloat()
            val roll = buf.readFloat()
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
                this.light = light
                this.faceToCamera = faceToCamera
                this.yaw = yaw
                this.pitch = pitch
                this.roll = roll
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
     *
     * 此选项会一直赋值给实际粒子
     *
     */
    var velocity: Vec3 = Vec3.ZERO

    /**
     * 生成的粒子是否始终面向摄像头
     *
     * 此选项会一直赋值给实际粒子
     */
    var faceToCamera = true

    /**
     * 如果faceToCamera为false
     * 则此参数代表了粒子水平朝向
     *
     * 弧度制
     *
     * 此选项会一直赋值给实际粒子
     */
    var yaw = 0.0f

    /**
     * 如果faceToCamera为false
     * 则此参数代表了粒子垂直朝向
     *
     * 弧度制
     *
     * 此选项会一直赋值给实际粒子
     */
    var pitch = 0.0f

    /**
     * 此参数代表了粒子滚动朝向
     *
     * 弧度制
     *
     * 此选项会一直赋值给实际粒子
     */
    var roll = 0.0f

    /**
     * 粒子大小
     *
     * 此选项会一直赋值给实际粒子
     */
    var size = 0.2f

    /**
     * 粒子生成时采用的不透明度
     *
     * 此选项会一直赋值给实际粒子
     */
    var alpha = 1f

    /**
     * 粒子生成时设置的age
     *
     * 此选项会一直赋值给实际粒子
     */
    var age = 0

    /**
     * 粒子最大生命周期
     *
     * 此选项会一直赋值给实际粒子
     */
    var maxAge = 120

    /**
     * 粒子生成时的亮度
     * (修改粒子亮度时请修改该数据)
     *
     * 此选项会一直赋值给实际粒子
     */
    var light = 15

    /**
     * 粒子可见范围
     */
    var visibleRange = 128f

    /**
     * 粒子样式 （必须是可控制的粒子）
     *
     * 此选项只生效一次
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

    /**
     * 粒子生成时采用的颜色，后续控制不生效
     *
     * 此选项只应用一次
     */
    var color = Vector3f(1f, 1f, 1f)

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

    /**
     * 快速旋转 yaw pitch 到目标点
     *
     * @param to 目标相对位置
     */
    fun setRotationTo(to: Vector3f) {
        val (x, y, z) = Math3DUtil.calculateEulerAnglesToPoint(to)
        this.yaw = y
        this.pitch = x
    }

    /**
     * 快速旋转 yaw pitch 到目标点
     *
     * @param to 目标相对位置
     */
    fun setRotationTo(to: Vec3) {
        setRotationTo(to.toVector3f())
    }

    /**
     * 快速旋转 yaw pitch 到目标点
     *
     * @param to 目标相对位置
     */
    fun setRotationTo(to: RelativeLocation) {
        setRotationTo(to.toVector3f())
    }

    fun getTextureSheet(): ParticleRenderType {
        return textureSheetFromString(textureSheet) ?: let {
            CooParticlesConstants.logger.error("can not find textureSheet $textureSheet you need use ControlableParticleData.registerRenderType() to register mapper")
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
            it.age = this.age
            it.maxAge = this.maxAge
            it.effect = this.effect.clone()
            it.textureSheet = this.textureSheet
            it.speed = this.speed
            it.sign = this.sign
            it.speedLimit = this.speedLimit
            it.light = this.light
            it.yaw = this.yaw
            it.pitch = this.pitch
            it.roll = this.roll
            it.faceToCamera = this.faceToCamera
        }
    }
}