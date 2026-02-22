package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersHelper
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.extend.lengthCoerceAtMost
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import cn.coostack.cooparticlesapi.utils.interpolator.emitters.LineEmitterInterpolator
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.HashMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 通用发射器实现，兼容多种可控对象：
 * - [ControlableParticleData]
 * - [ParticleComposition]
 * - [DisplayEntity]
 */
abstract class ClassEmitters(
    override var pos: Vec3,
    override var world: Level?,
) : ParticleEmitters {
    override var tick: Int = 0
    override var maxTick: Int = 120
    override var delay: Int = 0
    override var uuid: UUID = UUID.randomUUID()
    override var cancelled: Boolean = false
    override var playing: Boolean = false
    var airDensity = 0.0
    var gravity: Double = 0.0
    val handlerList = ConcurrentHashMap<String, SortedMap<ParticleEventHandler, Boolean>>()

    /**
     * 是否启用线段插值器。
     *
     * 如果设置为 `true`，每次发射会沿插值结果逐点生成；设置为 `false` 时，每 tick 只执行一次生成。
     */
    var enableInterpolator = false

    /**
     * 发射器位移插值器。
     *
     * 仅在 [enableInterpolator] 为 `true` 时生效，可通过 `setRefiner` 调整细分密度。
     */
    var emittersInterpolator: Interpolator = LineEmitterInterpolator()
        .setRefiner(5.0)

    override fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean) {
        val handlerID = handler.getHandlerID()
        if (!ParticleEventHandlerManager.hasRegister(handlerID)) {
            ParticleEventHandlerManager.register(handler)
        }
        val eventID = handler.getTargetEventID()
        val list = handlerList.getOrPut(eventID) { TreeMap() }
        list[handler] = innerClass
    }

    private fun addEventHandlerList(list: MutableList<ParticleEventHandler>) {
        val dirtyLists = HashMap<String, MutableList<ParticleEventHandler>>()
        list.forEach { handler ->
            val handlerID = handler.getHandlerID()
            if (!ParticleEventHandlerManager.hasRegister(handlerID)) {
                ParticleEventHandlerManager.register(handler)
            }
            val eventID = handler.getTargetEventID()
            val handlers = handlerList.getOrPut(eventID) { TreeMap() }
            handlers[handler] = false
        }
        dirtyLists.forEach {
            it.value.sortBy { h -> h.getPriority() }
        }
    }

    private fun collectEventHandles(): List<ParticleEventHandler> {
        return handlerList.flatMap {
            it.value.filter { item -> !item.value }.keys
        }
    }

    companion object {
        fun encodeBase(data: ClassEmitters, buf: FriendlyByteBuf) {
            val handles = data.collectEventHandles()
            buf.writeInt(handles.size)
            handles.forEach {
                buf.writeUtf(it.getHandlerID())
            }
            buf.writeVec3(data.pos)
            buf.writeInt(data.tick)
            buf.writeInt(data.maxTick)
            buf.writeInt(data.delay)
            buf.writeUUID(data.uuid)
            buf.writeBoolean(data.cancelled)
            buf.writeBoolean(data.playing)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeBoolean(data.enableInterpolator)
            buf.writeDouble(data.emittersInterpolator.refinerCount)
            buf.writeUtf(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /** 写法：先在 codec 的 decode 中创建对象，再把 `buf` 和 `container` 传入此方法，最后继续解码子类字段。 */
        fun decodeBase(container: ClassEmitters, buf: FriendlyByteBuf) {
            val handlerCount = buf.readInt()
            val handlers = ArrayList<ParticleEventHandler>()
            repeat(handlerCount) {
                val handleID = buf.readUtf()
                val handler = ParticleEventHandlerManager.getHandlerById(handleID)!!
                handlers.add(handler)
            }
            container.addEventHandlerList(handlers)

            val pos = buf.readVec3()
            val tick = buf.readInt()
            val maxTick = buf.readInt()
            val delay = buf.readInt()
            val uuid = buf.readUUID()
            val canceled = buf.readBoolean()
            val playing = buf.readBoolean()
            val gravity = buf.readDouble()
            val airDensity = buf.readDouble()
            val mass = buf.readDouble()
            val enableInterpolator = buf.readBoolean()
            val interpolatorCount = buf.readDouble()
            val windID = buf.readUtf()
            val wind = WindDirections.getCodecFromID(windID).decode(buf)
            container.apply {
                this.pos = pos
                this.tick = tick
                this.maxTick = maxTick
                this.delay = delay
                this.uuid = uuid
                this.cancelled = canceled
                this.playing = playing
                this.gravity = gravity
                this.airDensity = airDensity
                this.mass = mass
                this.wind = wind
                this.enableInterpolator = enableInterpolator
                this.emittersInterpolator.setRefiner(interpolatorCount)
            }
        }
    }

    /** 风力方向。 */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    /** 质量，单位 g。 */
    var mass: Double = 1.0

    override fun start() {
        if (playing) return
        playing = true
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
    }

    override fun stop() {
        cancelled = true
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (cancelled || !playing) return
        world ?: return
        doTick()
        if (!world!!.isClientSide) {
            increaseTick()
            return
        }

        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }

        if (tick % max(1, delay) == 0) {
            if (enableInterpolator) {
                val res = emittersInterpolator.getRefinedResult()
                val count = res.size
                res.forEachIndexed { index, relative ->
                    val current = relative.toVector()
                    val lerpProgress = index / (count - 1f)
                    doSubtick(current, lerpProgress)
                    spawnParticle(current, lerpProgress)
                }
            } else {
                spawnParticle(pos, 1f)
            }
        }
        increaseTick()
    }

    private fun increaseTick() {
        if (++tick >= maxTick && maxTick != -1) {
            stop()
        }
    }

    override fun spawnParticle(pos: Vec3, lerpProgress: Float) {
        if (!world!!.isClientSide) return
        val spawnWorld = world as ClientLevel
        val controls = genControls(lerpProgress)
        val total = controls.size.coerceAtLeast(1).toFloat()
        var spawnedCount = 0f
        controls.forEach { (data, relative) ->
            spawnedCount++
            val spawnPos = pos.add(relative.toVector())
            val particleLerpProgress = spawnedCount / total
            val control = data.createControler(
                spawnWorld,
                spawnPos,
                particleLerpProgress,
                lerpProgress
            )
            val displayed = data.getDisplayer().display(spawnPos, spawnWorld) ?: control
            singleControlableAction(
                displayed,
                data,
                RelativeLocation.of(spawnPos),
                spawnWorld,
                particleLerpProgress,
                lerpProgress
            )
            bindControlerMotion(displayed, data, spawnWorld)
        }
    }

    /**
     * 服务器和客户端都会执行此方法。
     *
     * 如需仅在服务端执行，请自行判断：
     * ```kotlin
     * if (!world!!.isClientSide) { ... }
     * ```
     */
    abstract fun doTick()

    /**
     * 可控对象生成器。
     *
     * @param lerpProgress 发射器位移插值器的插值进度
     */
    abstract fun genControls(lerpProgress: Float): List<Pair<SerializableData, RelativeLocation>>

    /**
     * 在一次生成前会执行。
     *
     * @param current 当前插值点的位置
     * @param lerpProgress 当前生成进度
     */
    protected open fun doSubtick(current: Vec3, lerpProgress: Float) {}

    /**
     * 通用可控对象的生成后回调。
     *
     * @param data 当前生成对象的数据
     * @param spawnPos 生成位置（对象创建后再改该值无效）
     * @param particleLerpProgress 当前对象在本次批量生成中的进度
     * @param posLerpProgress 发射器位移插值进度（未启用插值时恒为 1）
     */
    open fun singleControlableAction(
        controler: Controlable<*>,
        data: SerializableData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
    }

    /**
     * 处理单个 controler 的移动方案
     */
    protected open fun moveSingleControler(
        controler: Controlable<*>,
        data: SerializableData,
        to: Vec3,
        collide: BlockHitResult
    ) {
        controler.teleportTo(to)
    }

    /**
     * 读取当前 controler 世界坐标。
     *
     * 子类可重写以支持自定义可控对象，默认支持：
     * - [ControlableParticle]
     * - [ParticleComposition]
     * - [DisplayEntity]
     */
    protected open fun resolveControlerPos(
        controler: Controlable<*>,
        data: SerializableData
    ): Vec3? {
        val value = runCatching { controler.getControlObject() }.getOrNull() ?: return null
        return when (value) {
            is ControlableParticle -> value.loc
            is ParticleComposition -> value.position
            is DisplayEntity -> value.pos
            else -> null
        }
    }

    /**
     * 读取当前 controler 的速度向量。
     *
     * 子类可重写以支持自定义数据类型；默认对 [ControlableParticleData] 生效。
     */
    protected open fun resolveControlerVelocity(
        controler: Controlable<*>,
        data: SerializableData
    ): Vec3 {
        if (data is ControlableParticleData) {
            data.velocity = data.velocity.lengthCoerceAtMost(data.speedLimit)
            return data.velocity
        }
        return Vec3.ZERO
    }

    private fun bindControlerMotion(
        controler: Controlable<*>,
        data: SerializableData,
        spawnWorld: ClientLevel
    ) {
        val tickable = controler as? Tickable<*> ?: return
        @Suppress("UNCHECKED_CAST")
        (tickable as Tickable<Any>).addPreTickAction {
            val current = resolveControlerPos(controler, data) ?: return@addPreTickAction
            val velocity = resolveControlerVelocity(controler, data)
            if (velocity.lengthSqr() <= 0.001) {
                return@addPreTickAction
            }
            val to = current.add(velocity)
            val collide = if (velocity.length() <= 200) {
                PhysicsUtil.collide(current, velocity, spawnWorld)
            } else {
                BlockHitResult.miss(current, Direction.UP, BlockPos.containing(current))
            }
            moveSingleControler(controler, data, to, collide)
        }
    }


    /**
     * 数据同步需要实现此方法。
     *
     * @param emitters 需要同步到当前实例的发射器模板
     */
    override fun update(emitters: ParticleEmitters) {
        if (emitters !is ClassEmitters) return
        this.pos = emitters.pos
        this.world = emitters.world
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.uuid = emitters.uuid
        this.cancelled = emitters.cancelled
        this.playing = emitters.playing
        this.handlerList.putAll(emitters.handlerList)
        this.emittersInterpolator.setRefiner(emitters.emittersInterpolator.refinerCount)
        ParticleEmittersHelper.updateEmitter(this, emitters)
    }

}
