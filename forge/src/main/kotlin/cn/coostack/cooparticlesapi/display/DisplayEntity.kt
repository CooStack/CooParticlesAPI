package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.api.NetworkDirtyMarkable
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
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.UUID
import kotlin.math.PI

abstract class DisplayEntity(
    var pos: Vec3,
    var world: Level?
) : Controlable<DisplayEntity>, ServerControler<DisplayEntity>, Tickable<DisplayEntity>, NetworkDirtyMarkable {
    companion object {
        fun encodeBase(data: DisplayEntity, buf: PacketByteBuf) {
            buf.writeVec3(data.pos)
            buf.writeFloat(data.yaw)
            buf.writeFloat(data.pitch)
            buf.writeFloat(data.roll)
            buf.writeFloat(data.scale)
            buf.writeBoolean(data.valid)
            buf.writeUUID(data.controlUUID)
        }

        fun decodeBase(instance: DisplayEntity, buf: PacketByteBuf) {
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

    var visibleRange = 256.0

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
    private var networkStateDirty = true
    private var networkFullDirty = true
    private var lastNetworkPos = pos
    private var lastNetworkYaw = yaw
    private var lastNetworkPitch = pitch
    private var lastNetworkRoll = roll
    private var lastNetworkScale = scale
    private var lastNetworkValid = valid
    private var pendingRemoteState: RemoteState? = null
    private val preTickActions = ArrayList<DisplayEntity.() -> Unit>()
    private val postTickActions = ArrayList<DisplayEntity.() -> Unit>()

    var manageRotation = true

    override fun markDirty() {
        if (world?.isClientSide != true) {
            networkFullDirty = true
            networkStateDirty = true
        }
    }

    internal fun consumeNetworkFullDirty(): Boolean {
        val dirty = networkFullDirty
        networkFullDirty = false
        return dirty
    }

    internal fun consumeNetworkStateDirty(): Boolean {
        if (lastNetworkPos != pos || lastNetworkYaw != yaw || lastNetworkPitch != pitch ||
            lastNetworkRoll != roll || lastNetworkScale != scale || lastNetworkValid != valid
        ) {
            networkStateDirty = true
            lastNetworkPos = pos
            lastNetworkYaw = yaw
            lastNetworkPitch = pitch
            lastNetworkRoll = roll
            lastNetworkScale = scale
            lastNetworkValid = valid
        }
        val dirty = networkStateDirty
        networkStateDirty = false
        return dirty
    }

    internal fun applyRemoteState(position: Vec3, yaw: Float, pitch: Float, roll: Float, scale: Float) {
        pendingRemoteState = RemoteState(position, yaw, pitch, roll, scale)
    }

    abstract fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    )

    abstract fun getCodec(): ForgeStreamCodec<PacketByteBuf, DisplayEntity>

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

    fun position(lerp: Float): Vec3 {
        return GraphMathHelper.lerp(lerp, prevPos, pos)
    }

    fun yaw(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(yaw - prevYaw).toFloat()
        return prevYaw + lerp * delta
    }

    fun scale(lerp: Float): Float {
        return GraphMathHelper.lerp(lerp, prevScale, pos)
    }

    fun pitch(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(pitch - prevPitch).toFloat()
        return prevPitch + lerp * delta
    }

    fun roll(lerp: Float): Float {
        val delta = Math3DUtil.fixAngle(roll - prevRoll).toFloat()
        return prevRoll + lerp * delta
    }

    override fun tick() {
        val stableSize = preTickActions.size
        var index = 0
        while (index < stableSize) {
            preTickActions[index](this)
            index++
        }
        yaw %= 360
        pitch %= 360
        roll %= 360
        val remoteState = pendingRemoteState
        if (remoteState != null) {
            this.prevPos = pos
            this.prevYaw = yaw
            this.prevPitch = pitch
            this.prevRoll = roll
            this.prevScale = scale
            this.pos = remoteState.position
            this.yaw = remoteState.yaw
            this.pitch = remoteState.pitch
            this.roll = remoteState.roll
            this.scale = remoteState.scale
            pendingRemoteState = null
        } else {
            this.prevPos = pos
            this.prevYaw = yaw
            this.prevPitch = pitch
            this.prevRoll = roll
            this.prevScale = scale
        }
        postTickActions.forEach { it(this) }
    }

    final override fun addPreTickAction(action: DisplayEntity.() -> Unit): Tickable<DisplayEntity> {
        preTickActions.add(action)
        return this
    }

    final override fun addPreTickActionPost(action: DisplayEntity.() -> Unit): Tickable<DisplayEntity> {
        postTickActions.add(action)
        return this
    }

    open fun transformOffset(): Vec3 {
        return Vec3.ZERO
    }

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
        applyRemoteState(other.pos, other.yaw, other.pitch, other.roll, other.scale)
        this.valid = other.valid
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

    private data class RemoteState(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        val scale: Float,
    )
}
