package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.compat.TransformableCParticleEmitterBridge
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
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
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Quaternionfc
import java.util.UUID
import kotlin.math.max

abstract class TransformableCParticleEmitter(
    pos: Vec3,
    override var world: Level?,
) : ParticleEmitters {

    private val posState = dirty(pos)
    override var pos by posState

    override var tick: Int = 0

    override var maxTick: Int = 120

    override var delay: Int = 0

    override var uuid: UUID = UUID.randomUUID()

    override var canceled: Boolean = false

    override var playing: Boolean = false

    var airDensity = 0.0
    var gravity: Double = 0.0
    var mass: Double = 1.0

    val handlerList = ConcurrentHashMap<String, SortedMap<ParticleEventHandler, Boolean>>()

    var enableInterpolator = false

    var emittersInterpolator: Interpolator = LineEmitterInterpolator()
        .setRefiner(5.0)

    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    override fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean) {
        val handlerID = handler.getHandlerID()
        if (!ParticleEventHandlerManager.hasRegister(handlerID)) {
            ParticleEventHandlerManager.register(handler)
        }
        val eventID = handler.getTargetEventID()
        val list = handlerList.getOrPut(eventID) { TreeMap() }
        list[handler] = innerClass
    }

    override fun start() {
        if (playing) return
        playing = true
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
    }

    override fun stop() {
        canceled = true
    }

    override fun tick() {
        if (canceled || !playing) return
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
        val world = world as ClientLevel
        val controls = genControls(lerpProgress)
        val total = controls.size.coerceAtLeast(1).toFloat()
        var spawnedCount = 0f
        controls.forEach { (data, relative) ->
            spawnedCount++
            val spawnPos = pos.add(relative.toVector())
            val particleLerpProgress = spawnedCount / total
            if (!isVisibleToClient(data, spawnPos)) {
                return@forEach
            }
            val control = data.createControler(
                world,
                spawnPos,
                particleLerpProgress,
                lerpProgress
            )
            val displayed = data.getDisplayer().display(spawnPos, world) ?: control
            singleControlableAction(
                displayed,
                data,
                RelativeLocation.of(spawnPos),
                world,
                particleLerpProgress,
                lerpProgress
            )
        }
    }

    protected open fun isVisibleToClient(data: SerializableData, spawnPos: Vec3): Boolean {
        val visibleRange = when (data) {
            is ControlableParticleData -> data.visibleRange
            is DisplayEntityEmittersData -> data.visibleRange
            else -> -1f
        }
        if (visibleRange < 0f) return true
        val player = Minecraft.getInstance().player ?: return false
        return player.position().distanceTo(spawnPos) <= visibleRange
    }

    abstract fun doTick()

    abstract fun genControls(lerpProgress: Float): List<Pair<SerializableData, RelativeLocation>>

    protected open fun doSubtick(current: Vec3, lerpProgress: Float) {}

    abstract fun singleControlableAction(
        controler: Controlable<*>,
        data: SerializableData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    )

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is TransformableCParticleEmitter) return
        this.posState.setCodecValue(emitters.pos)
        this.world = emitters.world
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.uuid = emitters.uuid
        this.canceled = emitters.canceled
        this.playing = emitters.playing
        this.handlerList.putAll(emitters.handlerList)
        this.emittersInterpolator.setRefiner(emitters.emittersInterpolator.refinerCount)
        ParticleEmittersRegistryHelper.updateEmitter(this, emitters)
    }

    override fun getValue(): ParticleEmitters {
        return this
    }

    override fun markDirty() {
        if (world?.isClientSide != true) {
            ParticleEmittersManager.enqueueDirty(this)
        }
    }

    override fun remove() {
        canceled = true
    }

    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ClientLevel) return
        this.world = world
        this.pos = pos
        ParticleEmittersManager.spawnEmitters(this)
    }

    override fun isValid(): Boolean {
        return !canceled
    }

    override fun rotateAsAxis(radian: Double) {
    }

    override fun rotateToPoint(to: RelativeLocation) {
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
    }

    override fun teleportTo(to: Vec3) {
        if (pos == to) return
        pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }
}
