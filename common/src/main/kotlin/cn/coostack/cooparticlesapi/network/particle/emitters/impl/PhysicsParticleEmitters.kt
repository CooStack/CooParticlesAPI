package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleHitEntityEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnLiquidEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.type.EmittersShootTypes
import cn.coostack.cooparticlesapi.extend.ofFloored
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import com.ezylang.evalex.Expression
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Random
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.pow

class PhysicsParticleEmitters(
    override var pos: Vec3,
    override var world: Level?,
    var templateData: ControlableParticleData,
) : ParticleEmitters {
    override var tick: Int = 0
    override var maxTick: Int = 120
    override var delay: Int = 0
    override var uuid: UUID = UUID.randomUUID()
    override var cancelled: Boolean = false
    override var playing: Boolean = false
    override fun getEmittersID(): String {
        return ID
    }

    val handlerList = ConcurrentHashMap<String, SortedMap<ParticleEventHandler, Boolean>>()

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
            // 保留innerClass为false的
            it.value.filter { it ->
                !it.value
            }.keys
        }
    }

    var shootType = EmittersShootTypes.point()

    /**
     * 表达式规则基于 EvalEx-3.5.0项目
     * t生成时间 整数
     * 返回值是基于当前值的偏移量 (offset)
     */
    var evalEmittersXWithT = "0"
    var evalEmittersYWithT = "0"
    var evalEmittersZWithT = "0"

    var offset = Vec3(0.0, 0.0, 0.0)

    var gravity = 0.0
    val random = Random(System.currentTimeMillis())

    /**
     * 每tick生成粒子个数
     */
    var count = 1

    /**
     * 每tick实际生成的粒子个数会受此影响 随机范围(0 .. countRandom)
     */
    var countRandom = 0

    /**
     * 空气密度
     */
    var airDensity = 0.0

    /**
     * 风力方向
     */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO)

    /**
     * 质量
     * 单位 g
     */
    var mass: Double = 1.0

    companion object {
        // 物理常量
        @Deprecated("use PhysicContent")
        const val EARTH_GRAVITY = PhysicConstant.EARTH_GRAVITY

        @Deprecated("use PhysicContent")
        const val SEA_AIR_DENSITY = PhysicConstant.SEA_AIR_DENSITY

        @Deprecated("use PhysicContent")
        const val DRAG_COEFFICIENT = PhysicConstant.DRAG_COEFFICIENT

        @Deprecated("use PhysicContent")
        const val CROSS_SECTIONAL_AREA = PhysicConstant.CROSS_SECTIONAL_AREA
        val ID = "physics-emitters"
        val CODEC: StreamCodec<FriendlyByteBuf, ParticleEmitters> =
            StreamCodec.of<FriendlyByteBuf, ParticleEmitters>(
                { buf, data ->
                    data as PhysicsParticleEmitters
                    val handles = data.collectEventHandles()
                    buf.writeInt(handles.size)
                    handles.forEach {
                        val id = it.getHandlerID()
                        buf.writeUtf(id)
                    }
                    buf.writeInt(data.count)
                    buf.writeInt(data.countRandom)
                    buf.writeVec3(data.pos)
                    buf.writeInt(data.tick)
                    buf.writeInt(data.maxTick)
                    buf.writeInt(data.delay)
                    buf.writeUUID(data.uuid)
                    buf.writeBoolean(data.playing)
                    buf.writeBoolean(data.cancelled)
                    buf.writeDouble(data.gravity)
                    buf.writeDouble(data.airDensity)
                    buf.writeDouble(data.mass)
                    buf.writeUtf(data.wind.getID())
                    data.wind.getCodec().encode(buf, data.wind)
                    buf.writeUtf(data.evalEmittersXWithT, 32767)
                    buf.writeUtf(data.evalEmittersYWithT, 32767)
                    buf.writeUtf(data.evalEmittersZWithT, 32767)
                    buf.writeVec3(data.offset)
                    buf.writeUtf(data.shootType.getID(), 32767)
                    data.shootType.getCodec().encode(buf, data.shootType)
                    ControlableParticleData.PACKET_CODEC.encode(
                        buf,
                        data.templateData
                    )
                }, {
                    val handlerCount = it.readInt()
                    val handlerList = ArrayList<ParticleEventHandler>()
                    repeat(handlerCount) { index ->
                        val handleID = it.readUtf()
                        val handler = ParticleEventHandlerManager.getHandlerById(handleID)!!
                        handlerList.add(handler)
                    }
                    val count = it.readInt()
                    val countRandom = it.readInt()
                    val pos = it.readVec3()
                    val tick = it.readInt()
                    val maxTick = it.readInt()
                    val delay = it.readInt()
                    val uuid = it.readUUID()
                    val play = it.readBoolean()
                    val cancelled = it.readBoolean()
                    val gravity = it.readDouble()
                    val airDensity = it.readDouble()
                    val mass = it.readDouble()
                    val windID = it.readUtf()
                    val from = WindDirections.getCodecFromID(windID)
                    val wind = from.decode(it)
                    val xE = it.readUtf(32767)
                    val yE = it.readUtf(32767)
                    val zE = it.readUtf(32767)
                    val offset = it.readVec3()
                    val typeID = it.readUtf(32767)
                    val codec = EmittersShootTypes.fromID(typeID)!!
                    val type = codec.decode(it)
                    val templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                    PhysicsParticleEmitters(pos, null, templateData).apply {
                        this.count = count
                        this.countRandom = countRandom
                        this.tick = tick
                        this.shootType = type
                        this.maxTick = maxTick
                        this.delay = delay
                        this.uuid = uuid
                        this.playing = play
                        this.cancelled = cancelled
                        this.gravity = gravity
                        this.airDensity = airDensity
                        this.wind = wind
                        this.mass = mass
                        this.evalEmittersXWithT = xE
                        this.evalEmittersYWithT = yE
                        this.evalEmittersZWithT = zE
                        this.offset = offset
                        setup()
                    }
                }
            )
    }

    private var bufferX = Expression(evalEmittersXWithT)
    private var bufferY = Expression(evalEmittersYWithT)
    private var bufferZ = Expression(evalEmittersZWithT)

    fun setup() {
        bufferX = Expression(evalEmittersXWithT)
        bufferY = Expression(evalEmittersYWithT)
        bufferZ = Expression(evalEmittersZWithT)
    }

    override fun start() {
        wind.loadEmitters(this)
        if (playing) return
        playing = true
    }

    override fun stop() {
        cancelled = true
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (cancelled || !playing) {
            return
        }
        if (tick++ >= maxTick && maxTick != -1) {
            stop()
            return
        }
        offset = Vec3(
            bufferX
                .with("t", tick)
                .evaluate().numberValue.toDouble(),
            bufferY
                .with("t", tick)
                .evaluate().numberValue.toDouble(),
            bufferZ
                .with("t", tick)
                .evaluate().numberValue.toDouble()
        )
        world ?: return
        if (!world!!.isClientSide) {
            return
        }

        if (tick % max(1, delay) == 0) {
            // 执行粒子变更操作
            // 生成新粒子
            spawnParticle(pos, 1f)
        }
    }

    override fun spawnParticle(pos: Vec3, lerpProgress: Float) {
        if (!world!!.isClientSide) {
            return
        }
        val world = world as ClientLevel
        val currentPos = pos.add(offset)
        val actualCount = count + if (countRandom != 0) random.nextInt(countRandom) else 0
        val actualPositions = shootType.getPositions(currentPos, tick, actualCount)
        actualPositions.forEach {
            val newData = templateData.clone()
            val v = shootType.getDefaultDirection(
                newData.velocity,
                tick,
                it,
                currentPos
            ).normalize().scale(newData.speed)
            newData.velocity = v
            spawnParticle(world, it, newData)
        }
    }

    private fun spawnParticle(world: ClientLevel, pos: Vec3, data: ControlableParticleData) {
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = ParticleDisplayer.withSingle(effect)
        val control = ControlParticleManager.createControl(effect.controlUUID)
        control.initInvoker = {
            this.size = data.size
            this.color = data.color
            this.currentAge = data.age
            this.lifetime = data.maxAge
            this.textureSheet = data.getTextureSheet()
            this.particleAlpha = data.alpha
        }
        control.addPreTickAction {
            if (minecraftTick) return@addPreTickAction
            if (bounding.hasNaN()) return@addPreTickAction
            val blockPos = ofFloored(this.loc)
            val down = ofFloored(
                this.loc.subtract(
                    bounding.maxX - bounding.minX,
                    bounding.maxY - bounding.minY,
                    bounding.maxZ - bounding.minZ
                )
            )
            if (world.getChunk(blockPos) == null || world.getChunk(down) == null) return@addPreTickAction
            val statusPos = world.getBlockState(blockPos)
            val statusDown = world.getBlockState(down)
            onTheGround = !statusDown.getCollisionShape(world, down).isEmpty || !statusPos.getCollisionShape(
                world,
                blockPos
            ).isEmpty
        }
        // 事件层
        control.addPreTickAction {
            // 针对 ParticleHitEntityEvent
            val hitEntityHandlers = handlerList[ParticleHitEntityEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            // 判断事件触发
            val entities = world.getEntities(null, this.bounding) { true }
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
            // 针对 ParticleOnGroundEvent
            val hitEntityHandlers = handlerList[ParticleOnGroundEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            // 判断事件触发
            if (!this.onTheGround) {
                return@addPreTickAction
            }
            // 计算交点
            val block = ofFloored(this.loc)
            val prev = prevPos
            val res = world.getBlockState(block).getShape(world, block)
                .clip(prev, this.loc, block)
            val intersection = res?.location ?: this.loc
            val event = ParticleOnGroundEvent(
                this,
                data,
                ofFloored(this.loc),
                intersection,
                res!!
            )
            for ((handler, _) in hitEntityHandlers) {
                if (handler.getTargetEventID() != ParticleOnGroundEvent.EVENT_ID) {
                    continue
                }
                handler.handle(event)
                if (event.canceled) {
                    break
                }
            }
        }

        control.addPreTickAction {
            // 针对 ParticleOnLiquidEvent
            val hitEntityHandlers = handlerList[ParticleOnLiquidEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            // 判断事件触发
            val blockPos = ofFloored(this.loc)
            // 更新前上一个位置
            val beforeLiquid = (control.bufferedData["cross_liquid"] as? Boolean) ?: false
            // 判断现在的位置是不是液体
            if (!world.shouldTickBlocksAt(blockPos)) {
                return@addPreTickAction
            }
            val state = world.getBlockState(blockPos)
            val currentLiquid = !state.isSolid
            control.bufferedData["cross_liquid"] = currentLiquid
            if (beforeLiquid || !currentLiquid) {
                return@addPreTickAction
            }
            // 前一个tick不是液体 当前tick是液体则触发事件
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
        control.addPreTickAction {
            // 模拟粒子运动 速度
            teleportTo(
                this.loc.add(data.velocity)
            )
            updatePhysics(this.loc, data)
            if (currentAge++ >= lifetime) {
                remove()
            }
        }
        displayer.display(pos, world)
    }

    private fun updatePhysics(pos: Vec3, data: ControlableParticleData) {
        val v = data.velocity
        val speed = v.length()
        val gravityForce = Vec3(0.0, gravity, 0.0)
        val airResistanceForce = if (speed > 0.01) {
            val dragMagnitude = 0.5 * airDensity * DRAG_COEFFICIENT *
                    CROSS_SECTIONAL_AREA * speed.pow(2) * 0.05
            v.normalize().scale(-dragMagnitude)
        } else {
            Vec3.ZERO
        }
        if (!wind.hasLoadedEmitters()) {
            wind.loadEmitters(this)
        }

        val windForce = WindDirections.handleWindForce(
            wind, pos,
            airDensity, DRAG_COEFFICIENT, CROSS_SECTIONAL_AREA, v
        )

        val a = gravityForce
            .add(airResistanceForce)
            .add(windForce)

        data.velocity = v.add(a)
    }

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is PhysicsParticleEmitters) return
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.playing = emitters.playing
        this.cancelled = emitters.cancelled
        this.templateData = emitters.templateData
        this.handlerList.putAll(emitters.handlerList)
        this.pos = emitters.pos
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, ParticleEmitters> {
        return CODEC
    }
}