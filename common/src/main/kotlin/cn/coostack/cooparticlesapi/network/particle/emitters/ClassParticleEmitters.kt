package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleHitEntityEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnLiquidEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.CROSS_SECTIONAL_AREA
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.DRAG_COEFFICIENT
import cn.coostack.cooparticlesapi.extend.ofFloored
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.interpolator.LineInterpolator
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.pow

/**
 * 通过自定义类来实现一些发散性粒子样式
 * (实在懒得写表达式了)
 */
abstract class ClassParticleEmitters(
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
     * 是否启用线段插值器
     * 如果设置为true 则spawnParticles由线段插值器管控
     * 设置为false,则只会在 1tick内执行一次spawnParticles
     */
    var enableInterpolator = false

    /**
     * 插值器工具
     * 需要启用 enableInterpolator
     * @see enableInterpolator
     */
    val emittersInterpolator = LineInterpolator()
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
            // 保留innerClass为false的
            it.value.filter { it ->
                !it.value
            }.keys
        }
    }


    companion object {
        fun encodeBase(data: ClassParticleEmitters, buf: FriendlyByteBuf) {
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
            buf.writeBoolean(data.cancelled)
            buf.writeBoolean(data.playing)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeBoolean(data.enableInterpolator)
            buf.writeUtf(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /**
         * 写法
         * 先在codec的 decode方法中 创建此对象
         * 然后将buf和container 传入此方法
         * 然后继续decode自己的参数
         */
        fun decodeBase(container: ClassParticleEmitters, buf: FriendlyByteBuf) {
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
            val id = buf.readUtf()
            val wind = WindDirections.getCodecFromID(id)
                .decode(buf)
            container.apply {
                this.pos = pos
                this.tick = tick
                this.maxTick = maxTick
                this.delay = delay
                this.uuid = uuid
                this.cancelled = canceled
                this.airDensity = airDensity
                this.gravity = gravity
                this.mass = mass
                this.playing = playing
                this.airDensity = airDensity
                this.wind = wind
                this.enableInterpolator = enableInterpolator
            }

        }

    }

    /**
     * 风力方向
     */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    /**
     * 质量
     * 单位 g
     */
    var mass: Double = 1.0
    override fun start() {
        if (playing) return
        playing = true
        if (world?.isClientSide == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
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

        world ?: return
        doTick()
        if (!world!!.isClientSide) {
            increaseTick()
            return
        }
        emittersInterpolator.insertPoint(pos)
        if (tick % max(1, delay) == 0) {
            // 执行粒子变更操作
            // 生成新粒子
            // 进行线性插值
            if (enableInterpolator) {
                emittersInterpolator.getRefinedResult().forEach {
                    val pos = it.toVector()
                    doSubtick(pos) // 用于设置其他插值
                    spawnParticle(pos)
                }
            } else {
                spawnParticle(pos)
            }
        }
        increaseTick()
    }

    private fun increaseTick() {
        if (++tick >= maxTick && maxTick != -1) {
            stop()
        }
    }

    override fun spawnParticle(pos: Vec3) {
        if (!world!!.isClientSide) {
            return
        }
        val world = world as ClientLevel
        // 生成粒子样式
        var spawnedCount = 0f
        val particles = genParticles()
        val total = particles.size
        particles.forEach {
            spawnedCount++
            spawnParticle(world, pos.add(it.second.toVector()), it.first, spawnedCount / total)
        }
    }

    /**
     * 服务器和客户端都会执行此方法
     * 判断服务器清使用 if(!world!!.isClient)
     */
    abstract fun doTick()

    /**
     * 粒子样式生成器
     */
    abstract fun genParticles(): List<Pair<ControlableParticleData, RelativeLocation>>

    /**
     * 在一次粒子生成前会执行
     * @param current 当前插值的生成位置
     */
    protected open fun doSubtick(current: Vec3) {}

    /**
     * 如若要修改粒子的位置, 速度 属性
     * 请直接修改 ControlableParticleData
     * @param data 用于操作单个粒子属性的类
     * @param currentProgress 生成这个粒子的时候，当前的进度
     * 执行tick方法请使用
     * controler.addPreTickAction
     */
    abstract fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        currentProgress: Float
    )

    private fun spawnParticle(world: ClientLevel, pos: Vec3, data: ControlableParticleData, progress: Float) {
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
            // 针对 ParticleOnGroundEvent
            val hitEntityHandlers = handlerList[ParticleOnGroundEvent.EVENT_ID] ?: return@addPreTickAction
            if (hitEntityHandlers.isEmpty()) return@addPreTickAction
            // 判断事件触发
            if (!this.onTheGround) {
                return@addPreTickAction
            }
            val event = ParticleOnGroundEvent(this, data, ofFloored(this.loc))
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
        val p = RelativeLocation.of(pos)
        singleParticleAction(control, data, p, world, progress)
        control.addPreTickAction {
            // 模拟粒子运动 速度
            teleportTo(
                this.loc.add(data.velocity)
            )
            if (currentAge++ >= lifetime) {
                remove()
            }
        }
        displayer.display(p.toVector(), world)
    }

    protected fun updatePhysics(pos: Vec3, data: ControlableParticleData) {
        val m = mass / 1000
        val v = data.velocity
        val speed = v.length()
        val gravityForce = Vec3(0.0, -m * gravity, 0.0)
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
            .scale(1.0 / m)

        data.velocity = v.add(a)
    }


    /**
     * 数据同步需要实现此方法
     */
    override fun update(emitters: ParticleEmitters) {
        if (emitters !is ClassParticleEmitters) return
        this.pos = emitters.pos
        this.world = emitters.world
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.uuid = emitters.uuid
        this.cancelled = emitters.cancelled
        this.playing = emitters.playing
        this.handlerList.putAll(emitters.handlerList)
    }

}