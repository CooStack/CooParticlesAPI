package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.UUID
import kotlin.math.PI


/**
 * 类似于展示实体
 *
 * 如果是从group或者style生成， 则只会在client tick
 *
 * 如果是直接从Manager spawn 则会进行客户端/服务器 数据包同步
 *
 * 注意 由于此类默认会管理旋转， 如果想要自己写旋转顺序，应该在init函数中 manageRotation = false
 *
 * @constructor 继承者必须提供相同的构造函数 （否则无法使用codec生成器)
 *
 * 注册时会使用全称引用作为键值， 然后保存Codec
 */
abstract class DisplayEntity(
    var pos: Vec3,
    var world: Level?
) : Controlable<DisplayEntity>, ServerControler<DisplayEntity>, Tickable<DisplayEntity> {
    companion object {
        fun encodeBase(data: DisplayEntity, buf: FriendlyByteBuf) {
            buf.writeVec3(data.pos)
            buf.writeFloat(data.yaw)
            buf.writeFloat(data.pitch)
            buf.writeFloat(data.roll)
            buf.writeFloat(data.scale)
            buf.writeBoolean(data.valid)
            buf.writeUUID(data.controlUUID)
        }

        fun decodeBase(instance: DisplayEntity, buf: FriendlyByteBuf) {
            instance.apply {
                pos = buf.readVec3()
                yaw = buf.readFloat()
                pitch = buf.readFloat()
                roll = buf.readFloat()
                scale = buf.readFloat()
                valid = buf.readBoolean()
                controlUUID = buf.readUUID()
            }
        }

    }

    var controlUUID: UUID = UUID.randomUUID()

    var prevPos = pos


    var prevYaw = 0f

    var yaw = 0f

    var prevPitch = 0f

    var pitch = 0f

    var prevRoll = 0f

    var roll = 0f

    var prevScale = 1f
    var scale = 1f

    private var valid = true

    /**
     * 由 DisplayEntityManager计算模型旋转
     *
     * 以 renderCenterOffset 为中心进行旋转 yaw pitch roll
     *
     * 如果想要自己应用旋转， 请设置 manageRotation = false
     */
    var manageRotation = true

    /**
     * 渲染该实体
     *
     * 此方法已经为modelMatrixStack执行了位移变换
     *
     * 如果你在此方法内应用了类似
     * ```kotlin
     * MinecraftRendererUtil.applyAtPoint(
     *         offset, this
     *     ) {
     *         MinecraftRendererUtil.applyRotation(
     *             this, entity.yaw(lerp), entity.pitch(lerp), entity.roll(lerp)
     *         )
     *     }
     * ```
     * 请调用
     * ```kotlin
     * init{
     *  manageRotation = false
     * }
     * ```
     * 否则会出现旋转冲突， 也就是会有两倍速度的旋转 同时在前面写的translate也会出现问题
     *
     * 要是发现 当直接设置transformOffset的结果不等同于在rotate前写translate的结果时，
     * 那一定是 manageRotation = true
     *
     * 如果要使用自动旋转，世界坐标位移变换请重写 transformOffset
     * @param view 观察矩阵
     * @param proj 透视矩阵
     * @param modelMatrixStack 模型变换矩阵
     * @param delta tick progress
     * @param camera 观察摄像机对象
     */
    abstract fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    )

    abstract fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity>

    open fun canRender(
        view: Matrix4f, proj: Matrix4f, modelMatrixStack: PoseStack, lerp: Float, camera: Camera
    ): Boolean {
        return true
    }

    fun rotateFromAngles(stack: PoseStack, delta: Float) {
        MinecraftRendererUtil.applyRotation(
            stack, yaw(delta), pitch(delta), roll(delta)
        )
    }

    /**
     * 自助插值
     *
     * @param lerp  插值进度
     * @return 插值结果
     */
    fun position(lerp: Float): Vec3 {
        return GraphMathHelper.lerp(lerp, prevPos, pos)
    }

    /**
     * 自助插值
     *
     * @param lerp  插值进度
     * @return 插值结果
     */
    fun yaw(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(yaw - prevYaw).toFloat()
        return prevYaw + lerp * delta
    }

    fun scale(lerp: Float): Float {
        return GraphMathHelper.lerp(lerp, prevScale, scale)
    }

    /**
     * 自助插值
     *
     * @param lerp  插值进度
     * @return 插值结果
     */
    fun pitch(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(pitch - prevPitch).toFloat()
        return prevPitch + lerp * delta
    }

    fun roll(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(roll - prevRoll).toFloat()
        return prevRoll + lerp * delta
    }


    override fun tick() {
        yaw %= 360
        pitch %= 360
        roll %= 360
        this.prevPos = pos
        this.prevYaw = yaw
        this.prevPitch = pitch
        this.prevRoll = roll
        this.prevScale = scale
    }

    /**
     * 不做处理
     */
    final override fun addPreTickAction(action: DisplayEntity.() -> Unit): Tickable<DisplayEntity> {
        return this
    }

    /**
     * 位移偏移
     */
    open fun transformOffset(): Vec3 {
        return Vec3.ZERO
    }

    /**
     * 默认模型中心偏移 （渲染一个方块 此参数可以到方块的立体中心)
     *
     * @return
     */
    open fun renderCenterOffset(): Vec3 {
        return Vec3(0.5, 0.5, 0.5)
    }

    override fun controlUUID(): UUID {
        return controlUUID
    }

    override fun rotateToPoint(to: RelativeLocation) {
        lookAt(to.toVector())
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
        rotateToPoint(to)
        rotateAsAxis(radian)
    }


    override fun rotateAsAxis(radian: Double) {
        roll += (radian * 180 / PI).toFloat()
    }


    /**
     * 设置 yaw pitch 使得他的 “z”轴面向 direction
     *
     * @param direction 目标方向
     */
    fun lookAt(direction: Vec3) {
        val yaw = Math3DUtil.getYawFromLocation(direction) * 180 / PI
        val pitch = Math3DUtil.getPitchFromLocation(direction) * 180 / PI
        this.yaw = yaw.toFloat()
        this.pitch = pitch.toFloat()
    }

    override fun teleportTo(to: Vec3) {
        this.prevPos = to
        this.pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun remove() {
        valid = false
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }

    override fun getControlObject(): DisplayEntity {
        return this
    }

    open fun update(other: DisplayEntity) {
        this.pos = other.pos
        this.valid = other.valid
        this.yaw = other.yaw
        this.pitch = other.pitch
        this.roll = other.roll
        this.scale = other.scale
        CodecHelper.updateFields(this, other)
    }

    override fun spawn(world: Level, pos: Vec3) {
        DisplayEntityManager.spawn(
            this.apply {
                this.world = world
                this.pos = pos
            }
        )
    }

    override fun isValid(): Boolean {
        return valid
    }

    override fun getValue(): DisplayEntity {
        return this
    }
}