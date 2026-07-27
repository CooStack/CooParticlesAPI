package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import java.util.Vector

/**
 * GPU 粒子的 [Controlable] 句柄 — composition 兼容的关键.
 *
 * 是 (system, slot, generation) 的轻量封装 (flyweight):
 * teleport/rotate/remove 直接写入 SoA 存储, 槽位复用后旧句柄自动失效.
 * 默认不参与 composition tick。注册 action 后才会进入 tick 集合，未注册时没有逐粒子 tick 开销。
 */
class CParticleControlable(
    val system: CParticleSystem,
    val slot: Int,
    val generation: Int,
    private val uuid: UUID,
    private val world: ClientLevel?,
    initialYaw: Float = 0f,
    initialPitch: Float = 0f,
    initialRoll: Float = 0f,
    initialAxis: Vec3? = null,
) : Controlable<CParticleControlable>, Tickable<CParticleControlable> {

    private val snapshotYaw = initialYaw
    private val snapshotPitch = initialPitch
    private val snapshotRoll = initialRoll
    private val snapshotAxis = initialAxis ?: run {
        val base = slot * CParticleStore.STRIDE
        val offset = CParticleStore.OFF_AXIS
        Vec3(
            system.store.data[base + offset].toDouble(),
            system.store.data[base + offset + 1].toDouble(),
            system.store.data[base + offset + 2].toDouble(),
        )
    }
    private var invokeQueue: MutableList<CParticleControlable.() -> Unit>? = null
    private var postInvokeQueue: MutableList<CParticleControlable.() -> Unit>? = null

    val valid: Boolean get() = system.checkHandle(slot, generation)
    internal val hasTickActions: Boolean
        get() = !invokeQueue.isNullOrEmpty() || !postInvokeQueue.isNullOrEmpty()

    /** 当前世界坐标 (无效句柄返回 null) */
    val pos: Vec3? get() = system.scriptedGetPos(slot, generation)

    var velocity: Vec3?
        get() = system.scriptedGetVelocity(slot, generation)
        set(value) {
            if (value != null) system.scriptedSetVelocity(slot, generation, value)
        }

    var age: Int
        get() = system.scriptedGetAge(slot, generation) ?: 0
        set(value) {
            system.scriptedSetAge(slot, generation, value)
        }

    var pitch: Float
        get() = system.scriptedGetRotation(slot, generation)?.x ?: snapshotPitch
        set(value) {
            val current = system.scriptedGetRotation(slot, generation) ?: return
            setEulerAngles(value, current.y, current.z)
        }

    var yaw: Float
        get() = system.scriptedGetRotation(slot, generation)?.y ?: snapshotYaw
        set(value) {
            val current = system.scriptedGetRotation(slot, generation) ?: return
            setEulerAngles(current.x, value, current.z)
        }

    var roll: Float
        get() = system.scriptedGetRotation(slot, generation)?.z ?: snapshotRoll
        set(value) {
            val current = system.scriptedGetRotation(slot, generation) ?: return
            system.scriptedAddRoll(slot, generation, value - current.z)
        }

    /**
     * 读取当前粒子的状态副本。句柄失效时返回 null，修改副本不会影响当前粒子。
     *
     * 副本固定为 STATIC。系统只保存解析后的 UV，无法还原 sprite 和 effect，
     * 因此这两个字段为 null。
     */
    fun snapshot(): CParticle? = system.snapshot(slot, generation)?.also { it.axis = snapshotAxis }

    override fun controlUUID(): UUID = uuid

    override fun teleportTo(to: Vec3) {
        system.scriptedSetPos(slot, generation, to)
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }

    override fun rotateToPoint(to: RelativeLocation) {
        setRotationDirection(to.toVector3f())
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
        setRotationDirection(to.toVector3f())
        system.scriptedAddRoll(slot, generation, radian.toFloat())
    }

    override fun rotateAsAxis(radian: Double) {
        system.scriptedAddRoll(slot, generation, radian.toFloat())
    }

    /** 设置基础欧拉角，分量顺序为 pitch、yaw、roll。 */
    fun setEulerAngles(pitch: Float, yaw: Float, roll: Float) {
        system.scriptedSetRotation(slot, generation, yaw, pitch, roll)
    }

    fun setRotationDirection(direction: Vector3f?) {
        system.scriptedSetRotationDirection(slot, generation, direction?.let(::Vector3f))
    }

    fun setRotationDirection(direction: Vec3) {
        setRotationDirection(Vector3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat()))
    }

    /** 设置角速度，单位 rad/tick；分量顺序为 pitch、yaw、roll。 */
    fun setAngularVelocity(pitch: Float, yaw: Float, roll: Float) {
        system.scriptedSetAngularVelocity(slot, generation, Vector3f(pitch, yaw, roll))
    }

    fun setAngularVelocity(velocity: Vector3f) {
        system.scriptedSetAngularVelocity(slot, generation, Vector3f(velocity))
    }

    fun setColor(r: Float, g: Float, b: Float) {
        system.scriptedSetColor(slot, generation, r, g, b)
    }

    fun setColor(color: Vector3f) {
        setColor(color.x, color.y, color.z)
    }


    fun setAlpha(alpha: Float) {
        system.scriptedSetAlpha(slot, generation, alpha)
    }

    fun setSize(width: Float, height: Float) {
        system.scriptedSetSize(slot, generation, width, height)
    }

    fun setSize(scale: Float) {
        setSize(scale, scale)
    }

    override fun addPreTickAction(action: CParticleControlable.() -> Unit): CParticleControlable {
        val queue = invokeQueue ?: ArrayList<CParticleControlable.() -> Unit>().also { invokeQueue = it }
        queue.add(action)
        return this
    }

    override fun addPreTickActionPost(action: CParticleControlable.() -> Unit): CParticleControlable {
        val queue = postInvokeQueue ?: ArrayList<CParticleControlable.() -> Unit>().also { postInvokeQueue = it }
        queue.add(action)
        return this
    }

    override fun tick() {
        if (!valid) {
            clearActions()
            return
        }
        invokeStable(invokeQueue)
        if (valid) invokeStable(postInvokeQueue)
    }

    fun moveToWithPhysics(to: Vec3): BlockHitResult? {
        val current = pos ?: return null
        val activeWorld = world ?: return null
        val result = PhysicsUtil.collide(current, to.subtract(current), activeWorld)
        teleportTo(
            if (result.type == HitResult.Type.MISS) to
            else PhysicsUtil.fixBeforeCollidePosition(result)
        )
        return result
    }

    fun moveWithPhysics(): BlockHitResult? {
        val current = pos ?: return null
        val movement = velocity ?: return null
        val result = moveToWithPhysics(current.add(movement)) ?: return null
        if (result.type != HitResult.Type.MISS) {
            velocity = PhysicsUtil.collideMovement(result, movement)
        }
        return result
    }

    private fun invokeStable(queue: MutableList<CParticleControlable.() -> Unit>?) {
        if (queue == null) return
        val stableSize = queue.size
        var index = 0
        while (index < stableSize && index < queue.size && valid) {
            queue[index](this)
            index++
        }
    }

    private fun clearActions() {
        invokeQueue?.clear()
        postInvokeQueue?.clear()
    }

    override fun remove() {
        system.kill(slot, generation)
        clearActions()
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }

    override fun getControlObject(): CParticleControlable = this
}
