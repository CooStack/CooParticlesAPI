package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.compat.TransformableCParticleEmitterBridge
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import cn.coostack.cooparticlesapi.utils.interpolator.emitters.LineEmitterInterpolator
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Quaternionfc
import java.util.UUID
import kotlin.math.max

/**
 * 只生成 [ControlableCParticleData] 的可变换 GPU 粒子发射器。
 *
 * 每个实例拥有独立的 GPU 坐标空间。[space] 为 [CParticleEmitterSpace.LOCAL] 时，已经生成的粒子会随
 * 发射器一起平移、按 [emitterRotation] 旋转并等比缩放；[particleRotation] 只控制新粒子的出生方向。
 * 切换到 [CParticleEmitterSpace.WORLD] 后，新粒子改用世界坐标，
 * 行为与 [ClassParticleEmitters] 的 GPU 路径一致。两类粒子使用不同 system，切换模式不会移动旧粒子。
 *
 * 本类型直接实现 [ParticleEmitters]，不继承 [ClassParticleEmitters]。逐粒子控制器、粒子事件和死亡重生
 * 不属于 GPU system 能力，因此不提供 CPU 回退。
 *
 * @param pos 发射器世界坐标
 * @param world 发射器所在世界
 */
abstract class TransformableCParticleEmitter(
    /** 发射器当前的世界坐标；直接修改后最迟在下一次 tick 同步。 */
    override var pos: Vec3,
    override var world: Level?,
) : ParticleEmitters {

    /** 当前生命周期 tick。 */
    override var tick: Int = 0

    /** 生命周期上限；`-1` 表示不按 tick 自动结束。 */
    override var maxTick: Int = 120

    /** 两次发射之间的 tick 间隔；小于 `1` 时按每 tick 发射处理。 */
    override var delay: Int = 0

    /** 跨服务端和客户端识别同一发射器的 UUID。 */
    override var uuid: UUID = UUID.randomUUID()

    /** 是否已经结束生命周期。 */
    override var canceled: Boolean = false

    /** 是否已经启动。 */
    override var playing: Boolean = false

    /** 新粒子使用的坐标空间。切换不会改变已经生成的粒子。 */
    var space: CParticleEmitterSpace = CParticleEmitterSpace.LOCAL

    /** LOCAL 粒子所在的发射器空间旋转；变化时会带动已经生成的粒子。 */
    var emitterRotation: Quaternionf = Quaternionf()
        set(value) {
            require(value.isFinite && value.lengthSquared() > 1e-12f) {
                "rotation must be finite and non-zero"
            }
            field = Quaternionf(value).normalize()
        }

    /** 新生成粒子的出生位置和初速度旋转；不会改变已经生成的粒子。 */
    var particleRotation: Quaternionf = Quaternionf()
        set(value) {
            require(value.isFinite && value.lengthSquared() > 1e-12f) {
                "particle rotation must be finite and non-zero"
            }
            field = Quaternionf(value).normalize()
        }

    /** 发射器局部空间的等比缩放。必须为正数，保证 GPU 模拟可以换算回局部空间。 */
    var scale: Double = 1.0
        set(value) {
            require(value.isFinite() && value > 0.0) { "scale must be finite and positive" }
            field = value
        }

    /** 环境空气密度，用于生成 GPU 阻力。 */
    var airDensity: Double = 0.0

    /** 每 tick 向世界 Y 负方向施加的重力加速度。 */
    var gravity: Double = 0.0

    /** 与 [ClassParticleEmitters] 保持一致的质量数据；当前 GPU 力场不会读取它。 */
    var mass: Double = 1.0

    /** 风力方向。GPU 路径只支持非 relative 的全局风。 */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also { it.loadEmitters(this) }

    /** 是否沿发射器上一 tick 到当前 tick 的路径插值生成。 */
    var enableInterpolator: Boolean = false

    /** 发射路径插值器。 */
    var emittersInterpolator: Interpolator = LineEmitterInterpolator().setRefiner(5.0)

    /** 发射器在当前 tick 中产生的世界坐标位移。 */
    var emitterVelocity: Vec3 = Vec3.ZERO
        private set

    private var lastTickPos: Vec3 = pos
    private var lastSyncedTransform = TransformSnapshot.capture(this)

    /**
     * 返回当前 GPU system 使用的附加力场。
     *
     * 位置和方向参数都沿用 [ClassParticleEmitters] 的世界坐标语义。
     */
    open fun cparticleForces(): List<CParticleForce> = emptyList()

    /** 返回方块碰撞网格相对当前发射器中心的保证范围。 */
    open fun cparticleBlockCollisionRange(): Int = CParticleSystemManager.DEFAULT_BLOCK_COLLISION_RANGE

    /**
     * 拒绝注册逐粒子事件。
     *
     * @param handler 待注册事件处理器
     * @param innerClass 是否为实现类内部处理器
     * @throws UnsupportedOperationException 本发射器没有逐粒子 CPU 控制器
     */
    final override fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean) {
        throw UnsupportedOperationException("TransformableCParticleEmitter does not support per-particle events")
    }

    override fun start() {
        if (playing) return
        playing = true
        lastTickPos = pos
        emitterVelocity = Vec3.ZERO
        lastSyncedTransform = TransformSnapshot.capture(this)
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
    }

    override fun stop() {
        canceled = true
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (canceled || !playing) return
        val level = world ?: return
        doTick()
        emitterVelocity = pos - lastTickPos
        lastTickPos = pos

        if (!level.isClientSide) {
            syncServerTransformIfChanged()
            increaseTick()
            return
        }

        TransformableCParticleEmitterBridge.syncSystems(this)
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
        if (tick % max(1, delay) == 0) {
            if (enableInterpolator) {
                val refined = emittersInterpolator.getRefinedResult()
                val denominator = (refined.size - 1).coerceAtLeast(1).toFloat()
                refined.forEachIndexed { index, location ->
                    val spawnPos = location.toVector()
                    val lerpProgress = if (refined.size == 1) 1f else index / denominator
                    doSubtick(spawnPos, lerpProgress)
                    spawnParticle(spawnPos, lerpProgress)
                }
            } else {
                spawnParticle(pos, 1f)
            }
        }
        increaseTick()
    }

    override fun spawnParticle(pos: Vec3, lerpProgress: Float) {
        val level = world as? ClientLevel ?: return
        val player = Minecraft.getInstance().player ?: return
        val particles = genParticles(lerpProgress)
        val batchSize = particles.size
        particles.forEach { (data, relative) ->
            val worldPosition = TransformableCParticleEmitterBridge.resolveWorldPosition(
                this,
                pos,
                relative,
            ) ?: return@forEach
            if (player.position().distanceTo(worldPosition) > data.visibleRange) return@forEach
            TransformableCParticleEmitterBridge.trySpawn(
                this,
                level,
                pos,
                relative,
                data,
                batchSize,
            )
        }
    }

    /**
     * 服务器和客户端每 tick 都会调用，用于更新发射器自身状态。
     *
     * 实现必须保证两端执行结果可同步，不能在这里直接访问仅客户端存在的渲染对象。
     */
    abstract fun doTick()

    /**
     * 生成本次发射的 GPU 粒子及其相对位置。
     *
     * LOCAL 模式下相对位置属于发射器坐标空间；WORLD 模式下会直接加到生成世界坐标。
     *
     * @param lerpProgress 发射路径中的插值进度，范围为 `0..1`
     * @return 本次要生成的 GPU data 和位置
     */
    abstract fun genParticles(lerpProgress: Float): List<Pair<ControlableCParticleData, RelativeLocation>>

    /**
     * 在路径插值中的一次发射前调用。
     *
     * @param current 本次插值后的发射世界坐标
     * @param lerpProgress 发射路径中的插值进度，范围为 `0..1`
     */
    protected open fun doSubtick(current: Vec3, lerpProgress: Float) {}

    /**
     * 追加发射器空间旋转。
     *
     * LOCAL 模式下，已经生成的粒子位置和速度都会跟随整个空间旋转。
     *
     * @param rotation 发射器空间的增量四元数
     */
    fun rotateEmitter(rotation: Quaternionfc) {
        emitterRotation = Quaternionf(emitterRotation).mul(rotation)
        syncClientTransform()
    }

    /** 绕发射器局部 Z 轴追加旋转。 */
    fun rotateEmitter(radian: Double) {
        rotateEmitter(Quaternionf().rotateLocalZ(radian.toFloat()))
    }

    /**
     * 追加新粒子的出生旋转。
     *
     * 只改变后续新粒子的出生位置和初速度；已经生成的粒子保持生成时的运动方向。
     * 粒子四边形朝向仍只由 `cameraOption` 和粒子自身旋转字段决定。
     *
     * @param rotation 新粒子出生坐标系的增量四元数
     */
    fun rotateParticle(rotation: Quaternionfc) {
        particleRotation = Quaternionf(particleRotation).mul(rotation)
        syncClientTransform()
    }

    /** 绕新粒子出生坐标系的局部 Z 轴追加旋转。 */
    fun rotateParticle(radian: Double) {
        rotateParticle(Quaternionf().rotateLocalZ(radian.toFloat()))
    }

    /**
     * 按世界坐标增量移动发射器。
     *
     * @param offset 本次增加的世界坐标位移
     */
    fun translate(offset: Vec3) {
        teleportTo(pos + offset)
    }

    /**
     * 按世界坐标分量增量移动发射器。
     *
     * @param x X 轴增量
     * @param y Y 轴增量
     * @param z Z 轴增量
     */
    fun translate(x: Double, y: Double, z: Double) {
        translate(Vec3(x, y, z))
    }

    /**
     * 设置绝对等比缩放。
     *
     * @param new 大于零的缩放值
     */
    fun scale(new: Double) {
        scale = new
        syncClientTransform()
    }

    /**
     * 按倍率增量缩放。
     *
     * @param factor 大于零的缩放倍率
     */
    fun scaleBy(factor: Double) {
        require(factor.isFinite() && factor > 0.0) { "scale factor must be finite and positive" }
        scale(scale * factor)
    }

    override fun teleportTo(to: Vec3) {
        pos = to
        syncClientTransform()
    }

    override fun rotateToPoint(to: RelativeLocation) {
        val next = Quaternionf()
        Math3DUtil.rotateQuatToPoint(next, to)
        emitterRotation = next
        syncClientTransform()
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
        val next = Quaternionf()
        Math3DUtil.rotateQuatToPoint(next, to)
        next.rotateLocalZ(radian.toFloat())
        emitterRotation = next
        syncClientTransform()
    }

    override fun rotateAsAxis(radian: Double) {
        rotateEmitter(radian)
    }

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is TransformableCParticleEmitter || getEmittersID() != emitters.getEmittersID()) return
        pos = emitters.pos
        world = world ?: emitters.world
        tick = emitters.tick
        maxTick = emitters.maxTick
        delay = emitters.delay
        uuid = emitters.uuid
        canceled = emitters.canceled
        playing = emitters.playing
        space = emitters.space
        emitterRotation = Quaternionf(emitters.emitterRotation)
        particleRotation = Quaternionf(emitters.particleRotation)
        scale = emitters.scale
        gravity = emitters.gravity
        airDensity = emitters.airDensity
        mass = emitters.mass
        wind = emitters.wind.also { it.loadEmitters(this) }
        enableInterpolator = emitters.enableInterpolator
        emittersInterpolator.setRefiner(emitters.emittersInterpolator.refinerCount)
        ParticleEmittersRegistryHelper.updateEmitter(this, emitters)
        resetRuntimeState(addInterpolatorPoint = false)
        syncClientTransform()
    }

    /** 结束后停止接收新粒子，并在已有粒子死亡后释放专属 GPU systems。 */
    internal fun finishClientSystems() {
        TransformableCParticleEmitterBridge.finishEmitter(uuid)
    }

    private fun syncClientTransform() {
        if (world?.isClientSide == true) {
            TransformableCParticleEmitterBridge.syncSystems(this)
        }
    }

    private fun syncServerTransformIfChanged() {
        val current = TransformSnapshot.capture(this)
        if (current == lastSyncedTransform) return
        ParticleEmittersManager.updateEmitters(this)
        lastSyncedTransform = current
    }

    private fun increaseTick() {
        if (++tick >= maxTick && maxTick != -1) {
            stop()
        }
    }

    private fun resetRuntimeState(addInterpolatorPoint: Boolean) {
        lastTickPos = pos
        emitterVelocity = Vec3.ZERO
        lastSyncedTransform = TransformSnapshot.capture(this)
        if (addInterpolatorPoint && enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
    }

    private data class TransformSnapshot(
        val pos: Vec3,
        val rotationX: Float,
        val rotationY: Float,
        val rotationZ: Float,
        val rotationW: Float,
        val particleRotationX: Float,
        val particleRotationY: Float,
        val particleRotationZ: Float,
        val particleRotationW: Float,
        val scale: Double,
        val space: CParticleEmitterSpace,
    ) {
        companion object {
            fun capture(emitter: TransformableCParticleEmitter): TransformSnapshot {
                val rotation = emitter.emitterRotation
                return TransformSnapshot(
                    emitter.pos,
                    rotation.x,
                    rotation.y,
                    rotation.z,
                    rotation.w,
                    emitter.particleRotation.x,
                    emitter.particleRotation.y,
                    emitter.particleRotation.z,
                    emitter.particleRotation.w,
                    emitter.scale,
                    emitter.space,
                )
            }
        }
    }

    companion object {
        /** 编码所有可变换 GPU emitter 共用的网络字段。 */
        fun encodeBase(data: TransformableCParticleEmitter, buf: FriendlyByteBuf) {
            buf.writeVec3(data.pos)
            buf.writeInt(data.tick)
            buf.writeInt(data.maxTick)
            buf.writeInt(data.delay)
            buf.writeUUID(data.uuid)
            buf.writeBoolean(data.canceled)
            buf.writeBoolean(data.playing)
            buf.writeByte(data.space.networkId)
            buf.writeQuaternion(data.emitterRotation)
            buf.writeQuaternion(data.particleRotation)
            buf.writeDouble(data.scale)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeBoolean(data.enableInterpolator)
            buf.writeDouble(data.emittersInterpolator.refinerCount)
            buf.writeUtf(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /** 解码所有可变换 GPU emitter 共用的网络字段。 */
        fun decodeBase(container: TransformableCParticleEmitter, buf: FriendlyByteBuf) {
            container.pos = buf.readVec3()
            container.tick = buf.readInt()
            container.maxTick = buf.readInt()
            container.delay = buf.readInt()
            container.uuid = buf.readUUID()
            container.canceled = buf.readBoolean()
            container.playing = buf.readBoolean()
            container.space = CParticleEmitterSpace.fromNetworkId(buf.readUnsignedByte().toInt())
            container.emitterRotation = buf.readQuaternion()
            container.particleRotation = buf.readQuaternion()
            container.scale = buf.readDouble()
            container.gravity = buf.readDouble()
            container.airDensity = buf.readDouble()
            container.mass = buf.readDouble()
            container.enableInterpolator = buf.readBoolean()
            container.emittersInterpolator.setRefiner(buf.readDouble())
            val windId = buf.readUtf()
            container.wind = WindDirections.getCodecFromID(windId).decode(buf).also {
                it.loadEmitters(container)
            }
            container.resetRuntimeState(addInterpolatorPoint = true)
        }
    }
}
