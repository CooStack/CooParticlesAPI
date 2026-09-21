package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleEmitterBridge
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.lengthCoerceAtMost
import cn.coostack.cooparticlesapi.extend.ofFloored
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.*
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.PhysicsUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.Interpolator
import cn.coostack.cooparticlesapi.utils.interpolator.emitters.LineEmitterInterpolator
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.pow

abstract class ClassParticleEmitters(
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
    private var lastTickPos: Vec3 = pos
    var emitterVelocity: Vec3 = Vec3.ZERO
        private set
    val handlerList = ConcurrentHashMap<String, SortedMap<ParticleEventHandler, Boolean>>()

    var enableInterpolator = false

    var emittersInterpolator: Interpolator = LineEmitterInterpolator()
        .setRefiner(5.0)

    override fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean) {
        val handlerID = handler.getHandlerID()
        if (!ParticleEventHandlerManager.hasRegister(handlerID)) {
            ParticleEventHandlerManager.register(handler)
        }
        val eventID = handler.getTargetEventID()
        val handlerList = handlerList.getOrPut(eventID) { TreeMap() }
        handlerList[handler] = innerClass
    }

    private fun addEventHandlerList(list: MutableList<ParticleEventHandler>) {
        val dirtyLists = HashMap<String, MutableList<ParticleEventHandler>>()
        list.forEach { handler ->
            val handlerID = handler.getHandlerID()
            if (!ParticleEventHandlerManager.hasRegister(handlerID)) {
                ParticleEventHandlerManager.register(handler)
            }
            val eventID = handler.getTargetEventID()
            val handlerList = handlerList.getOrPut(eventID) { TreeMap() }
            handlerList[handler] = false
        }
        dirtyLists.forEach {
            it.value.sortBy { it -> it.getPriority() }
        }
    }

    private fun collectEventHandles(): List<ParticleEventHandler> {
        return handlerList.flatMap {
            it.value.filter { it ->
                !it.value
            }.keys
        }
    }

    companion object {
        fun encodeBase(data: ClassParticleEmitters, buf: PacketByteBuf) {
            val handles = data.collectEventHandles()
            buf.writeInt(handles.size)
            handles.forEach {
                val id = it.getHandlerID()
                buf.writeUtf(id)
            }
            buf.writeVec3(data.pos)
            buf.writeInt(data.tick)
            buf.writeInt(data.maxTick)
            buf.writeInt(data.delay)
            buf.writeUUID(data.uuid)
            buf.writeBoolean(data.canceled)
            buf.writeBoolean(data.playing)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeBoolean(data.enableInterpolator)
            buf.writeDouble(data.emittersInterpolator.refinerCount)
            buf.writeUtf(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        fun decodeBase(container: ClassParticleEmitters, buf: PacketByteBuf) {
            val handlerCount = buf.readInt()
            val handlerList = ArrayList<ParticleEventHandler>()
            repeat(handlerCount) {
                val handleID = buf.readUtf()
                val handler = ParticleEventHandlerManager.getHandlerById(handleID)!!
                handlerList.add(handler)
            }
            container.addEventHandlerList(handlerList)
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
            val id = buf.readUtf()
            val wind = WindDirections.getCodecFromID(id)
                .decode(buf)
            container.apply {
                this.posState.setCodecValue(pos)
                this.tick = tick
                this.maxTick = maxTick
                this.delay = delay
                this.uuid = uuid
                this.canceled = canceled
                this.airDensity = airDensity
                this.gravity = gravity
                this.mass = mass
                this.playing = playing
                this.airDensity = airDensity
                this.wind = wind
                this.enableInterpolator = enableInterpolator
                this.emittersInterpolator.setRefiner(interpolatorCount)
            }
        }
    }

    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    open fun cparticleForces(): List<CParticleForce> = emptyList()

    open fun submitCParticleForces(sink: CParticleForceSink) {
        sink.submitAll(cparticleForces())
    }

    open fun cparticleBlockCollisionRange(): Int = CParticleSystemManager.DEFAULT_BLOCK_COLLISION_RANGE

    var mass: Double = 1.0
    override fun start() {
        if (playing) return
        playing = true
        lastTickPos = pos
        emitterVelocity = Vec3.ZERO
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
    }

    override fun stop() {
        canceled = true
    }

    override fun tick() {
        if (canceled || !playing) {
            return
        }

        world ?: return
        val previousPos = lastTickPos
        doTick()
        emitterVelocity = pos.subtract(previousPos)
        lastTickPos = pos
        if (!world!!.isClientSide) {
            increaseTick()
            return
        }
        CParticleEmitterBridge.syncSystems(this)
        if (enableInterpolator) {
            emittersInterpolator.insertPoint(pos)
        }
        if (tick % max(1, delay) == 0) {
            if (enableInterpolator) {
                val res = emittersInterpolator.getRefinedResult()
                val count = res.size
                res.forEachIndexed { index, it ->
                    val pos = it.toVector()
                    val lerpProgress = index / (count - 1F)
                    doSubtick(pos, lerpProgress)
                    spawnParticle(pos, lerpProgress)
                }
            } else {
                spawnParticle(pos, 1F)
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
        if (!world!!.isClientSide) {
            return
        }
        val world = world as ClientLevel
        var spawnedCount = 0F
        val particles = genParticles(lerpProgress)
        val total = particles.size
        val cparticleBatchSize = particles.count { it.first is ControlableCParticleData }
        particles.forEach {
            spawnedCount++
            spawnParticle(
                world,
                pos.add(it.second.toVector()),
                it.first,
                spawnedCount / total,
                lerpProgress,
                cparticleBatchSize,
            )
        }
    }

    abstract fun doTick()

    abstract fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>>

    protected open fun doSubtick(current: Vec3, lerpProgress: Float) {}

    abstract fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    )

    open fun singleParticleDeathAction(
        oldControler: ParticleControler,
        oldData: ControlableParticleData,
        respawnCount: Int,
        reason: RemoveReason
    ): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf()
    }

    private fun spawnParticle(
        world: ClientLevel,
        pos: Vec3,
        data: ControlableParticleData,
        particleLerpProgress: Float,
        posLerpProgress: Float,
        cparticleBatchSize: Int,
    ) {
        val player = Minecraft.getInstance().player ?: return
        if (player.position().distanceTo(pos) > data.visibleRange) {
            return
        }
        if (data is ControlableCParticleData &&
            CParticleEmitterBridge.trySpawn(this, world, pos, data, cparticleBatchSize)
        ) {
            return
        }
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = data.getDisplayer()
        val control = data.createControler(world, pos, particleLerpProgress, posLerpProgress) as ParticleControler
        control.addPreTickAction {
            val hitEntityHandlers = handlerList[ParticleHitEntityEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            val entities =
                world.getEntitiesOfClass(Entity::class.java, this.bounding.expandTowards(0.5, 0.5, 0.5)) { true }
            if (entities.isEmpty()) return@addPreTickAction
            val first = entities.first()
            val event = ParticleHitEntityEvent(this, data, first)
            for ((handler, _) in hitEntityHandlers) {
                if (handler.getTargetEventID() != ParticleHitEntityEvent.EVENT_ID) {
                    continue
                }
                handler.handle(event)
                if (event.canceled) {
                    break
                }
            }
        }

        control.addPreTickAction {
            val hitEntityHandlers = handlerList[ParticleOnLiquidEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            val blockPos = ofFloored(this.loc)
            val beforeLiquid = (control.bufferedData["cross_liquid"] as? Boolean) ?: false
            val state = world.getBlockState(blockPos)
            val currentLiquid = !state.isSolid
            control.bufferedData["cross_liquid"] = currentLiquid
            if (beforeLiquid || !currentLiquid) {
                return@addPreTickAction
            }
            val event = ParticleOnLiquidEvent(this, data, blockPos)
            for ((handler, _) in hitEntityHandlers) {
                if (handler.getTargetEventID() != ParticleOnLiquidEvent.EVENT_ID) {
                    continue
                }
                handler.handle(event)
                if (event.canceled) {
                    break
                }
            }
        }
        val p = RelativeLocation.of(pos)
        singleParticleAction(control, data, p, world, particleLerpProgress, posLerpProgress)
        control.applyDestroyAction {
            val newParticles =
                singleParticleDeathAction(control, data, data.respawnCount + 1, it)
            val respawnCParticleBatchSize = newParticles.count { (newData, _) ->
                newData is ControlableCParticleData
            }
            newParticles.forEach { (newData, rel) ->
                newData.respawnCount = data.respawnCount + 1
                spawnParticle(
                    world,
                    this.loc.add(rel.toVector()),
                    newData,
                    particleLerpProgress,
                    posLerpProgress,
                    respawnCParticleBatchSize,
                )
            }
        }
        control.addPreTickAction {
            if (currentAge++ >= lifetime) {
                remove()
            }
            if (minecraftTick) return@addPreTickAction
            if (bounding.hasNaN()) return@addPreTickAction

            data.velocity = data.velocity.lengthCoerceAtMost(data.speedLimit)
            val prepareMove = this.loc.add(data.velocity)
            val clipRes = if (data.velocity.lengthSqr() > 0.001) {
                if (data.velocity.length() <= 200) {
                    PhysicsUtil.collide(this.loc, data.velocity, world)
                } else {
                    BlockHitResult.miss(this.loc, Direction.UP, BlockPos.containing(this.loc))
                }
            } else {
                BlockHitResult.miss(this.loc, Direction.UP, BlockPos.containing(this.loc))
            }
            onTheGround = clipRes.type != HitResult.Type.MISS && clipRes.direction == Direction.UP
            moveSingleParticleWithVelocity(this, data, prepareMove, clipRes)
            if (onTheGround) {
                val offset = clipRes.direction.normal.asVec3() * 0.1
                val event = ParticleOnGroundEvent(
                    this,
                    data,
                    ofFloored(prepareMove),
                    clipRes.location.add(offset),
                    clipRes
                )
                for ((handler, _) in (handlerList[ParticleOnGroundEvent.EVENT_ID] ?: emptyMap())) {
                    if (handler.getTargetEventID() != ParticleOnGroundEvent.EVENT_ID) {
                        continue
                    }
                    handler.handle(event)
                    if (event.canceled) {
                        break
                    }
                }
            }

            if (clipRes.type != HitResult.Type.MISS) {
                val event = ParticleCollideEvent(
                    this, data, clipRes
                )
                for ((handler, _) in (handlerList[ParticleCollideEvent.EVENT_ID] ?: emptyMap())) {
                    if (handler.getTargetEventID() != ParticleCollideEvent.EVENT_ID) {
                        continue
                    }
                    handler.handle(event)
                    if (event.canceled) {
                        break
                    }
                }
            }
        }
        if (displayer.display(p.toVector(), world) == null) {
            control.remove(RemoveReason.QUEUE)
        }
    }

    fun updatePhysics(pos: Vec3, data: ControlableParticleData, particle: ControlableParticle) {
        val v = data.velocity
        val speed = v.length()
        val gravity = if (particle.onTheGround) 0.0 else gravity
        val gravityForce = Vec3(0.0, -gravity, 0.0)
        val airResistanceForce = if (speed > 0.01) {
            val dragMagnitude = 0.5 * airDensity * PhysicConstant.DRAG_COEFFICIENT *
                    PhysicConstant.CROSS_SECTIONAL_AREA * speed.pow(2) * 0.05
            v.normalize().scale(-dragMagnitude)
        } else {
            Vec3.ZERO
        }

        if (!wind.hasLoadedEmitters()) {
            wind.loadEmitters(this)
        }

        val windForce = WindDirections.handleWindForce(
            wind, pos,
            airDensity, PhysicConstant.DRAG_COEFFICIENT, PhysicConstant.CROSS_SECTIONAL_AREA, v
        )

        val a = gravityForce
            .add(airResistanceForce)
            .add(windForce)

        data.velocity = v.add(a)
    }

    protected open fun moveSingleParticleWithVelocity(
        particle: ControlableParticle,
        data: ControlableParticleData,
        to: Vec3,
        collide: BlockHitResult
    ) {
        particle.teleportTo(to)
    }

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is ClassParticleEmitters) return
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
}
