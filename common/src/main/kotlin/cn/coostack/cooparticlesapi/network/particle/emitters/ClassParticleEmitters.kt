package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersHelper
import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.extend.lengthCoerceAtMost
import cn.coostack.cooparticlesapi.extend.ofFloored
import cn.coostack.cooparticlesapi.extend.times
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.*
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.CROSS_SECTIONAL_AREA
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.DRAG_COEFFICIENT
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
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.pow

/** 通过自定义类来实现一些发散性粒子样式 (实在懒得写表达式了) */
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
     * 是否启用线段插值器 如果设置为true 则spawnParticles由线段插值器管控 设置为false,则只会在
     * 1tick内执行一次spawnParticles
     */
    var enableInterpolator = false

    /**
     * 插值器工具 需要启用 enableInterpolator 可以修改
     *
     * @see enableInterpolator
     */
    var emittersInterpolator: Interpolator = LineEmitterInterpolator()
        .setRefiner(5.0)

    override fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean) {
        val handlerID = handler.getHandlerID()
        // 自动注册不适用于多人
        // TODO
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
            buf.writeDouble(data.emittersInterpolator.refinerCount)
            buf.writeUtf(data.wind.getID())
            data.wind.getCodec().encode(buf, data.wind)
        }

        /** 写法 先在codec的 decode方法中 创建此对象 然后将buf和container 传入此方法 然后继续decode自己的参数 */
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
            val interpolatorCount = buf.readDouble()
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
                this.emittersInterpolator.setRefiner(interpolatorCount)

            }

        }

    }

    /** 风力方向 */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    /** 质量 单位 g */
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
        if (cancelled || !playing) {
            return
        }

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
            // 执行粒子变更操作
            // 生成新粒子
            // 进行线性插值
            if (enableInterpolator) {
                val res = emittersInterpolator.getRefinedResult()
                val count = res.size
                res.forEachIndexed { index, it ->
                    val pos = it.toVector()
                    val lerpProgress = index / (count - 1f)
                    doSubtick(pos, lerpProgress) // 用于设置其他插值
                    spawnParticle(pos, lerpProgress)
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
        if (!world!!.isClientSide) {
            return
        }
        val world = world as ClientLevel
        // 生成粒子样式
        var spawnedCount = 0f
        val particles = genParticles(lerpProgress)
        val total = particles.size
        particles.forEach {
            spawnedCount++
            spawnParticle(world, pos.add(it.second.toVector()), it.first, spawnedCount / total, lerpProgress)
        }
    }

    /**
     * 服务器和客户端都会执行此方法 判断服务器清使用
     *
     *  ```kotlin
     *  if(!world!!.isClient)
     *  ```
     */
    abstract fun doTick()

    /**
     * 粒子样式生成器
     * @param lerpProgress 粒子发射器位移插值器的插值进度
     * */
    abstract fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>>

    /**
     * 在一次粒子生成前会执行
     *
     * @param current 当前插值的生成位置
     * @param lerpProgress 粒子生成的进度
     */
    protected open fun doSubtick(current: Vec3, lerpProgress: Float) {}

    /**
     * 如若要修改粒子的位置, 速度 属性 请直接修改 ControlableParticleData
     *
     * @param data 用于操作单个粒子属性的类
     * @param spawnPos 生成的位置，在粒子已经生成后再修改无效
     *    执行tick方法请使用controler.addPreTickAction
     * @param particleLerpProgress
     *    生成这个粒子的时候，当前的进度（(当前生成的粒子索引+1)/genParticles(lerpProgress: Float).size ）
     * @param posLerpProgress 当发射器进行发射插值时， 插值的偏移 如果不使用插值则永远为1
     */
    abstract fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    )

    /**
     * 当粒子死亡（age >= maxAge然后remove）时 会执行这个方法
     *
     * @param oldControler
     * @param oldData
     * @param respawnCount 重新生成的次数 （0代表第一次从emitter生成）
     * @param reason 粒子移除原因
     * @return 会根据data生成新粒子 第二个pair是和粒子死亡位置的相对位置(不是发射器相对位置)
     */
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
        posLerpProgress: Float
    ) {

        val player = Minecraft.getInstance().player ?: return
        if (player.position().distanceTo(pos) > data.visibleRange) {
            return
        }
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = data.getDisplayer()
        val control = data.createControler(world, pos, particleLerpProgress, posLerpProgress) as ParticleControler
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
        singleParticleAction(control, data, p, world, particleLerpProgress, posLerpProgress)
        control.applyDestroyAction {
            // 要有粒子死因
            // 生成新粒子
            val newParticles =
                singleParticleDeathAction(control, data, data.respawnCount + 1, it)
            newParticles.forEach { (newData, rel) ->
                newData.respawnCount = data.respawnCount + 1
                spawnParticle(world, this.loc.add(rel.toVector()), newData, particleLerpProgress, posLerpProgress)
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
            // 模拟粒子运动 速度
            moveSingleParticleWithVelocity(this, data, prepareMove, clipRes)
            if (onTheGround) {
                // 找方向 velocity
                handlerList[ParticleOnGroundEvent.EVENT_ID]?.let {
                    val offset = clipRes.direction.normal.asVec3() * 0.1
                    val event = ParticleOnGroundEvent(
                        this,
                        data,
                        ofFloored(prepareMove),
                        clipRes.location.add(offset),
                        clipRes
                    )
                    for ((handler, _) in it) {
                        if (handler.getTargetEventID() != ParticleOnGroundEvent.EVENT_ID) {
                            continue
                        }
                        handler.handle(event)
                        if (event.canceled) {
                            break
                        }
                    }
                }
            }

            if (clipRes.type != HitResult.Type.MISS) {
                handlerList[ParticleCollideEvent.EVENT_ID]?.let {
                    val event = ParticleCollideEvent(
                        this, data, clipRes
                    )
                    for ((handler, _) in it) {
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
        }
        displayer.display(p.toVector(), world)
    }

    fun updatePhysics(pos: Vec3, data: ControlableParticleData, particle: ControlableParticle) {
        val v = data.velocity
        val speed = v.length()
        val gravity = if (particle.onTheGround) 0.0 else gravity
        val gravityForce = Vec3(0.0, -gravity, 0.0) // 下面质量会被消除掉
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

    /**
     * # 处理单个粒子的位移位置
     * - 方便更真实的物理模拟
     * - 用于修改particle受到velocity时的移动
     *
     * @param particle 被操作位置的粒子
     * @param data 被操作位置的粒子数据
     * @param to 正常情况下粒子应该移动到的位置（单纯loc+velocity）
     * @param collide 粒子的碰撞情况 （如果显示碰撞，则代表to位置存在方块 loc不存在）
     */
    protected open fun moveSingleParticleWithVelocity(
        particle: ControlableParticle,
        data: ControlableParticleData,
        to: Vec3,
        collide: BlockHitResult
    ) {
        particle.teleportTo(to)
    }

    /**
     * 数据同步需要实现此方法
     *
     * @param emitters 更新的模板发射器
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
        this.emittersInterpolator.setRefiner(emitters.emittersInterpolator.refinerCount)
        ParticleEmittersHelper.updateEmitter(this, emitters)
    }

}