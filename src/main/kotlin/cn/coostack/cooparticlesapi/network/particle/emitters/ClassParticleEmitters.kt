package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandlerManager
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleHitEntityEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnGroundEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleOnLiquidEvent
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.CROSS_SECTIONAL_AREA
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.DRAG_COEFFICIENT
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
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
    override var pos: Vec3d,
    override var world: World?,
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
        fun encodeBase(data: ClassParticleEmitters, buf: PacketByteBuf) {
            val handles = data.collectEventHandles()
            buf.writeInt(handles.size)
            handles.forEach {
                val id = it.getHandlerID()
                buf.writeString(id)
            }
            buf.writeVec3d(data.pos)
            buf.writeInt(data.tick)
            buf.writeInt(data.maxTick)
            buf.writeInt(data.delay)
            buf.writeUuid(data.uuid)
            buf.writeBoolean(data.cancelled)
            buf.writeBoolean(data.playing)
            buf.writeDouble(data.gravity)
            buf.writeDouble(data.airDensity)
            buf.writeDouble(data.mass)
            buf.writeString(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /**
         * 写法
         * 先在codec的 decode方法中 创建此对象
         * 然后将buf和container 传入此方法
         * 然后继续decode自己的参数
         */
        fun decodeBase(container: ClassParticleEmitters, buf: PacketByteBuf) {
            val handlerCount = buf.readInt()
            val handlerList = ArrayList<ParticleEventHandler>()
            repeat(handlerCount) {
                val handleID = buf.readString()
                val handler = ParticleEventHandlerManager.getHandlerById(handleID)!!
                handlerList.add(handler)
            }
            container.addEventHandlerList(handlerList)
            val pos = buf.readVec3d()
            val tick = buf.readInt()
            val maxTick = buf.readInt()
            val delay = buf.readInt()
            val uuid = buf.readUuid()
            val canceled = buf.readBoolean()
            val playing = buf.readBoolean()
            val gravity = buf.readDouble()
            val airDensity = buf.readDouble()
            val mass = buf.readDouble()
            val id = buf.readString()
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
            }

        }

    }

    /**
     * 风力方向
     */
    var wind: WindDirection = GlobalWindDirection(Vec3d.ZERO).also {
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
        if (world?.isClient == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun stop() {
        cancelled = true
        if (world?.isClient == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (cancelled || !playing) {
            return
        }

        world ?: return
        doTick()
        if (!world!!.isClient) {
            increaseTick()
            return
        }
        if (tick % max(1, delay) == 0) {
            // 执行粒子变更操作
            // 生成新粒子
            spawnParticle()
        }
        increaseTick()
    }

    private fun increaseTick() {
        if (++tick >= maxTick && maxTick != -1) {
            stop()
        }
    }

    override fun spawnParticle() {
        if (!world!!.isClient) {
            return
        }
        val world = world as ClientWorld
        // 生成粒子样式
        genParticles().forEach {
            spawnParticle(world, pos.add(it.value.toVector()), it.key)
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
    abstract fun genParticles(): Map<ControlableParticleData, RelativeLocation>

    /**
     * 如若要修改粒子的位置, 速度 属性
     * 请直接修改 ControlableParticleData
     * @param data 用于操作单个粒子属性的类
     * 执行tick方法请使用
     * controler.addPreTickAction
     */
    abstract fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    )

    private fun spawnParticle(world: ClientWorld, pos: Vec3d, data: ControlableParticleData) {
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = ParticleDisplayer.withSingle(effect)
        val control = ControlParticleManager.createControl(effect.controlUUID)
        control.initInvoker = {
            this.size = data.size
            this.color = data.color
            this.currentAge = data.age
            this.maxAge = data.maxAge
            this.textureSheet = data.getTextureSheet()
            this.particleAlpha = data.alpha
        }
        control.addPreTickAction {
            if (minecraftTick) return@addPreTickAction
            if (bounding.isNaN) return@addPreTickAction
            val blockPos = BlockPos.ofFloored(this.pos)
            val down = BlockPos.ofFloored(
                this.pos.subtract(
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
            val entities = world.getOtherEntities(null, this.bounding.expand(0.5, 0.5, 0.5)) { true }
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
            val event = ParticleOnGroundEvent(this, data, BlockPos.ofFloored(this.pos))
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
            val blockPos = BlockPos.ofFloored(this.pos)
            // 更新前上一个位置
            val beforeLiquid = (control.bufferedData["cross_liquid"] as? Boolean) ?: false
            // 判断现在的位置是不是液体
            if (!world.shouldTickBlockPos(blockPos)) {
                return@addPreTickAction
            }
            val state = world.getBlockState(blockPos)
            val currentLiquid = state.isLiquid
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

        singleParticleAction(control, data, pos, world)
        control.addPreTickAction {
            // 模拟粒子运动 速度
            teleportTo(
                this.pos.add(data.velocity)
            )
            if (currentAge++ >= maxAge) {
                markDead()
            }
        }
        displayer.display(pos, world)
    }

    protected fun updatePhysics(pos: Vec3d, data: ControlableParticleData) {
        val m = mass / 1000
        val v = data.velocity
        val speed = v.length()
        val gravityForce = Vec3d(0.0, -m * gravity, 0.0)
        val airResistanceForce = if (speed > 0.01) {
            val dragMagnitude = 0.5 * airDensity * DRAG_COEFFICIENT *
                    CROSS_SECTIONAL_AREA * speed.pow(2) * 0.05
            v.normalize().multiply(-dragMagnitude)
        } else {
            Vec3d.ZERO
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
            .multiply(1.0 / m)

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