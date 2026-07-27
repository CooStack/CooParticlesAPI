package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.CooParticlesConstants
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
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID

open class ControlableParticleData : SerializableData {
    companion object {
        @JvmStatic
        val particleTexturesMapper: MutableMap<String, ParticleRenderType> = mutableMapOf()

        @JvmStatic
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, ControlableParticleData> =
            StreamCodec.of(
                ::encodeBase,
                { buf -> decodeBase(buf, ControlableParticleData()) },
            )

        /**
         * 编码所有基础粒子字段，供父类和扩展 data 共用。
         *
         * Example: `ControlableCParticleData` 先调用此方法，再写入自己的纹理来源。
         * Forbidden: 子类不能改变这些字段的顺序，否则旧的基础 data 无法解码。
         *
         * @param buf 目标网络缓冲区
         * @param data 要编码的基础粒子数据
         */
        internal fun encodeBase(buf: RegistryFriendlyByteBuf, data: ControlableParticleData) {
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

            ParticleTypes.STREAM_CODEC.encode(buf, data.effect)

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

        /**
         * 从缓冲区读取基础字段并写入指定实例。
         *
         * Example: 子类传入自己的新实例，解码后仍保留实际运行时类型。
         * Forbidden: 不要在这里固定构造 `ControlableParticleData`，否则子类字段会丢失。
         *
         * @param buf 来源网络缓冲区
         * @param target 接收基础字段的实例
         * @return 赋值完成的 [target]
         */
        internal fun <T : ControlableParticleData> decodeBase(
            buf: RegistryFriendlyByteBuf,
            target: T,
        ): T {
            val uuid = buf.readUUID()
            val velocity = buf.readVec3()
            val weightSize = buf.readFloat()
            val heightSize = buf.readFloat()
            val uniformSize = buf.readBoolean()
            val visibleRange = buf.readFloat()
            val color = buf.readVector3f()
            val alpha = buf.readFloat()
            val age = buf.readInt()
            val maxAge = buf.readInt()
            val textureSheet = buf.readUtf()
            val effect = ParticleTypes.STREAM_CODEC.decode(buf) as ControlableParticleEffect
            effect.controlUUID = uuid
            val speed = buf.readDouble()
            val speedLimit = buf.readDouble()
            val sign = buf.readInt()
            val light = buf.readInt()
            val cameraOption = ParticleCameraOption.STREAM_CODEC.decode(buf)
            val axis = buf.readVec3()
            val yaw = buf.readFloat()
            val pitch = buf.readFloat()
            val roll = buf.readFloat()
            val depthSize = buf.readFloat()
            return target.apply {
                this.uuid = uuid
                this.velocity = velocity
                this.color = color
                this.alpha = alpha
                this.uniformSize = uniformSize
                this.weightSize = weightSize
                this.heightSize = heightSize
                this.depthSize = depthSize
                this.visibleRange = visibleRange
                this.age = age
                this.maxAge = maxAge
                this.textureSheet = textureSheet
                this.effect = effect
                this.speed = speed
                this.sign = sign
                this.speedLimit = speedLimit
                this.light = light
                this.cameraOption = cameraOption
                this.axis = axis
                this.yaw = yaw
                this.pitch = pitch
                this.roll = roll
            }
        }

        @JvmStatic
        fun registerRenderType(type: ParticleRenderType) {
            particleTexturesMapper[type.toString()] = type
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
     */
    var velocity: Vec3 = Vec3.ZERO

    /**
     * 生成的粒子相机朝向模式，默认始终面向摄像头。
     */
    var cameraOption: ParticleCameraOption = ParticleCameraOption.BILLBOARD

    /** 旧布尔 API 的兼容桥接：true = BILLBOARD，false = ROTATION。 */
    var faceToCamera: Boolean
        get() = cameraOption == ParticleCameraOption.BILLBOARD
        set(value) {
            cameraOption = ParticleCameraOption.fromFaceToCamera(value)
        }

    /** AXIS_BILLBOARD 使用的固定轴方向。 */
    var axis: Vec3 = Vec3(0.0, 1.0, 0.0)

    /**
     * 如果faceToCamera为false
     * 则此参数代表了粒子水平朝向
     *
     * 弧度制
     */
    var yaw = 0.0f

    /**
     * 如果faceToCamera为false
     * 则此参数代表了粒子垂直朝向
     *
     * 弧度制
     *
     */
    var pitch = 0.0f

    /**
     * 此参数代表了粒子滚动朝向
     *
     * 弧度制
     *
     */
    var roll = 0.0f

    /** 是否保持宽高等比。 */
    var uniformSize = true

    /** 粒子宽度大小。 */
    private var currentWeightSize = 0.2f
    var weightSize: Float
        get() = currentWeightSize
        set(value) {
            currentWeightSize = value
            if (uniformSize) {
                currentHeightSize = value
            }
        }

    /** 粒子高度大小。 */
    private var currentHeightSize = 0.2f
    var heightSize: Float
        get() = currentHeightSize
        set(value) {
            currentHeightSize = value
            if (uniformSize) {
                currentWeightSize = value
            }
        }

    private var currentDepthSize = 0f
    var depthSize: Float
        get() = currentDepthSize
        set(value) {
            currentDepthSize = value
        }

    /** 旧 size 兼容属性：设置时同时修改宽高。 */
    var size: Float
        get() = (weightSize + heightSize) / 2f
        set(value) {
            currentWeightSize = value
            currentHeightSize = value
        }

    /**
     * 粒子生成时采用的不透明度
     *
     */
    var alpha = 1f

    /**
     * 粒子生成时设置的age
     *
     */
    var age = 0

    /**
     * 粒子最大生命周期
     *
     */
    var maxAge = 120

    /**
     * 粒子生成时的亮度
     * (修改粒子亮度时请修改该数据)
     *
     */
    var light = 15

    /**
     * 粒子可见范围
     */
    var visibleRange = 256f

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

    /**
     * 粒子重生次数
     * 他会在不断的粒子重生中递增
     */
    var respawnCount = 0
        internal set

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

    fun setAxisLocation(axis: RelativeLocation) {
        this.axis = Vec3(axis.x, axis.y, axis.z)
    }

    fun getTextureSheet(): ParticleRenderType {
        return textureSheetFromString(textureSheet) ?: let {
            CooParticlesConstants.logger.error("can not find textureSheet $textureSheet you need use ControlableParticleData.registerRenderType() to register mapper")
            ParticleRenderType.PARTICLE_SHEET_OPAQUE
        }
    }

    /**
     * 如果你的参数暴露在外面 可能会被服务器调用
     * 则使用这个
     *
     * 输入的参数是你的目标的RenderType的toString的内容
     *
     * @param value
     */
    fun setTextureSheet(value: String) {
        this.textureSheet = value
    }

    fun setTextureSheet(value: TextureSheetsEnum){
        this.textureSheet = value.name
    }

    /**
     * 如果你的参数暴露在外面 （可能会被服务器环境调用）
     * 那就不要使用这个方法 使用字符串的
     * 因为脑残的设计导致服务器无法访问 ParticleRenderType 类
     * [setTextureSheet(String)]
     *
     * @param value
     */
    fun setTextureSheet(value: ParticleRenderType) {
        this.textureSheet = value.toString()
    }

    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, out ControlableParticleData> {
        return PACKET_CODEC
    }

    override fun createControler(
        world: ClientLevel,
        pos: Vec3,
        particleLerpProcess: Float,
        posLerpProcess: Float
    ): Controlable<*> {
        val control = ControlParticleManager.createControl(effect.controlUUID)
        val data = this
        control.applyInitializedAction {
            this.uniformSize = data.uniformSize
            this.weightSize = data.weightSize
            this.heightSize = data.heightSize
            this.depthSize = data.depthSize
            this.color = data.color
            this.currentAge = data.age
            this.lifetime = data.maxAge
            this.light = data.light
            this.textureSheet = data.getTextureSheet()
            this.particleAlpha = data.alpha
            this.cameraOption = data.cameraOption
            this.axis = data.axis
            this.previewAxis = data.axis
            this.previewWeightSize = data.weightSize
            this.previewHeightSize = data.heightSize
            this.previewDepthSize = data.depthSize
            this.currentPitch = data.pitch
            this.currentYaw = data.yaw
            this.currentRoll = data.roll
            this.previewPitch = data.pitch
            this.previewYaw = data.yaw
            this.previewRoll = data.roll
        }
        return control
    }

    override fun getDisplayer(): ParticleDisplayer {
        return ParticleDisplayer.withSingle(effect)
    }

    override fun clone(): ControlableParticleData {
        return ControlableParticleData().also(::copyTo)
    }

    /**
     * 把可克隆的基础字段复制到另一个 data，并为目标生成新 UUID。
     *
     * Example: 扩展类可调用 `super.copyTo(target)` 后补充自己的字段。
     * Forbidden: 网络解码不能调用此方法，因为网络中的 UUID 必须原样保留。
     *
     * @param target 接收当前基础字段的实例
     */
    protected fun copyTo(target: ControlableParticleData) {
        target.uuid = UUID.randomUUID()
        target.velocity = velocity
        target.uniformSize = uniformSize
        target.weightSize = weightSize
        target.heightSize = heightSize
        target.depthSize = depthSize
        target.color = color
        target.alpha = alpha
        target.visibleRange = visibleRange
        target.age = age
        target.maxAge = maxAge
        target.effect = effect.clone()
        target.textureSheet = textureSheet
        target.speed = speed
        target.sign = sign
        target.speedLimit = speedLimit
        target.light = light
        target.yaw = yaw
        target.pitch = pitch
        target.roll = roll
        target.cameraOption = cameraOption
        target.axis = axis
    }
}
