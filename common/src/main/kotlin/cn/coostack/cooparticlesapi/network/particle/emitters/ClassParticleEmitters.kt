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
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDeathCommand
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDeathContext
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleRespawnRequest
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.event.*
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
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
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathBirth
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.pow

/** 通过自定义类来实现一些发散性粒子样式 (实在懒得写表达式了) */
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

    /**
     * 普通粒子和 GPU 粒子共用的死亡指令，默认不启用。
     *
     * 示例：`deathCommand = ParticleDeathCommand { listOf(respawn(nextTemplate)) }`。
     * 设置后替代旧版 [singleParticleDeathAction]，避免同一次死亡重复生成。
     * 在 emitter 构造或初始化中配置；闭包本身不参与网络同步。
     * 每颗粒子出生时固定所用指令，默认只重生一代。
     * CPU 死亡时执行闭包；GPU 出生时预计算后继树，死亡时在 GPU 激活已上传模板。
     */
    var deathCommand: ParticleDeathCommand? = null
    private var lastTickPos: Vec3 = pos
    var emitterVelocity: Vec3 = Vec3.ZERO
        private set
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

    /** 风力方向 */
    var wind: WindDirection = GlobalWindDirection(Vec3.ZERO).also {
        it.loadEmitters(this)
    }

    /**
     * 旧版 GPU 力场列表接口。
     *
     * 旧 emitter 可以继续覆写此方法；桥接层会在每 tick 调用
     * [submitCParticleForces]，由默认实现把列表内容提交到 sink。
     * 新 emitter 应直接覆写流式接口，避免创建临时列表。
     *
     * @return 按执行顺序排列的力场列表
     */
    open fun cparticleForces(): List<CParticleForce> = emptyList()

    /**
     * GPU 模式附加力场 (每 tick 同步一次)。
     *
     * 默认实现兼容旧版 [cparticleForces]；新 emitter 可以直接向 sink 提交，
     * 从而避免每 tick 构建临时列表。内置 ParticleCommand 可用
     * [CParticleForce.fromCommand] 转换。
     *
     * @param sink 当前 emitter 的力场快照，调用期间只应提交本 emitter 的力
     */
    open fun submitCParticleForces(sink: CParticleForceSink) {
        sink.submitAll(cparticleForces())
    }

    /**
     * CParticle 方块碰撞相对 system 原点的最大保证范围，单位为方块。
     *
     * 示例：粒子最远会离开 system 原点 40 格时返回 `40`。
     * 禁止：返回值会被限制在 `0..96`；范围越大，网格刷新成本按立方增长。
     */
    open fun cparticleBlockCollisionRange(): Int = CParticleSystemManager.DEFAULT_BLOCK_COLLISION_RANGE

    /** 质量 单位 g */
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
            // 执行粒子变更操作
            // 生成新粒子
            // 进行线性插值
            if (enableInterpolator) {
                val res = emittersInterpolator.getRefinedResult()
                val count = res.size
                res.forEachIndexed { index, it ->
                    val pos = it.toVector()
                    val lerpProgress = index / (count - 1F)
                    doSubtick(pos, lerpProgress) // 用于设置其他插值
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
        // 生成粒子样式
        var spawnedCount = 0F
        val particles = genParticles(lerpProgress)
        val total = particles.size
        val cparticleBatchSize = particles.count { it.first is ControlableCParticleData }
        CParticleEmitterBridge.beginBatch(uuid)
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
     * 该旧入口仅支持普通粒子，保留调用方自行限制代数的语义。跨 CPU/GPU 请使用 [deathCommand]。
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
        posLerpProgress: Float,
        cparticleBatchSize: Int,
    ) {

        val player = Minecraft.getInstance().player ?: return
        if (player.position().distanceTo(pos) > data.visibleRange) {
            return
        }
        // cparticle GPU 路径: 数据直接进 GPU 粒子系统, 跳过 controler/事件/碰撞
        if (data is ControlableCParticleData &&
            CParticleEmitterBridge.trySpawn(this, world, pos, data, cparticleBatchSize)
        ) {
            return
        }
        val effect = data.effect
        effect.controlUUID = data.uuid
        val displayer = data.getDisplayer()
        val control = data.createControler(world, pos, particleLerpProgress, posLerpProgress) as ParticleControler
        control.recordPathBirth(CParticlePathBirth(pos.subtract(this.pos), data.age.toDouble()))
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
        val command = deathCommand
        val birthData = if (command?.acceptsGeneration(data.respawnCount) == true) data.clone() else null
        var deathHandled = false
        control.applyDestroyAction { reason ->
            if (deathHandled) return@applyDestroyAction
            deathHandled = true
            if (command != null) {
                if (birthData != null && reason != RemoveReason.QUEUE) {
                    spawnDeathParticles(command, ParticleDeathContext(
                        birthData, loc, data.velocity, currentAge, reason,
                        this@ClassParticleEmitters.pos, data.respawnCount,
                    ), world, particleLerpProgress, posLerpProgress)
                }
                return@applyDestroyAction
            }
            // 要有粒子死因
            // 生成新粒子
            val newParticles =
                singleParticleDeathAction(control, data, data.respawnCount + 1, reason)
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
                return@addPreTickAction
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
            // 路径位置约束在运动之后生效：默认运动实现会把位置写成 loc + velocity，
            // 因此必须在这里覆盖，才能保证路径写入后不再叠加一次速度积分。
            applyPendingPathConstraint(this)
            if (death) return@addPreTickAction
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
        if (displayer.display(p.toVector(), world) == null) {
            control.remove(RemoveReason.QUEUE)
        }
    }

    /** 在客户端统一执行死亡指令；新 data 的种类决定普通或 GPU 路径。 */
    internal fun spawnDeathParticles(
        command: ParticleDeathCommand,
        context: ParticleDeathContext,
        world: ClientLevel,
        particleLerpProgress: Float = 1F,
        posLerpProgress: Float = 1F,
    ) {
        if (Minecraft.getInstance().level !== world) return
        val requests = command.createParticles(context.copy(emitterPosition = pos))
        spawnPreparedDeathParticles(requests, world, particleLerpProgress, posLerpProgress)
    }

    /** 生成已求值请求；GPU 到 CPU 的异步分支不能再次执行原死亡配置闭包。 */
    internal fun spawnPreparedDeathParticles(
        requests: List<ParticleRespawnRequest>, world: ClientLevel,
        particleLerpProgress: Float = 1F, posLerpProgress: Float = 1F,
    ) {
        if (Minecraft.getInstance().level !== world) return
        val gpuCount = requests.count { it.data is ControlableCParticleData }
        try {
            for (request in requests) {
                spawnParticle(world, request.position, request.data, particleLerpProgress, posLerpProgress, gpuCount)
            }
        } finally {
            // 发射器停止发射后，已出生粒子仍可完成有限重生，尾部 system 不重新常驻。
            if (canceled) CParticleEmitterBridge.finishEmitter(this)
        }
    }

    fun updatePhysics(pos: Vec3, data: ControlableParticleData, particle: ControlableParticle) {
        val v = data.velocity
        val speed = v.length()
        val gravity = if (particle.onTheGround) 0.0 else gravity
        val gravityForce = Vec3(0.0, -gravity, 0.0) // 下面质量会被消除掉
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
     * 应用本 tick 由路径位置约束命令登记的位置与朝向。
     *
     * 路径命令在发射器运动之前执行，其求值结果暂存在
     * [cn.coostack.cooparticlesapi.particles.control.ParticleControler]；这里在位移与碰撞完成后覆盖位置，
     * 因此路径位置不会被再叠加一次速度积分。隐藏与寿命仍然沿用粒子自己的流程。
     *
     * ## 坐标空间
     * 路径几何位于**持有者的本地空间**，求值结果也是本地偏移。传统 [ControlableParticle] 的
     * `teleportTo` 接收的是**世界坐标**，因此必须在这里加上发射器位置；直接把本地偏移当世界坐标
     * 会把粒子送到世界原点附近，而不是发射器周围。
     * GPU 侧不需要这一步：那里的粒子位置本身就是 system 原点（即发射器位置）相对坐标。
     *
     * 朝向仅在命令显式开启时更新，按声明的纹理前向轴求解 XYZ 欧拉角。
     */
    private fun applyPendingPathConstraint(particle: ControlableParticle) {
        val request = particle.controler.consumePendingPathRequest() ?: return
        if (!request.isActive()) return
        val target = request.pathPosition().add(pos)
        if (request.pathWrapped()) {
            // 循环跳转：同时重置渲染插值的历史位置，避免画出终点到起点的错误穿越轨迹。
            particle.teleportToResettingInterpolation(target)
        } else {
            particle.teleportTo(target)
        }
        val direction = request.pathDirection()
        if (request.pathFacesMotion() && direction.lengthSqr() > 1.0E-12) {
            particle.cameraOption = ParticleCameraOption.ROTATION
            request.pathRotation()?.let { rotation ->
                particle.setPathRotation(rotation, request.pathWrapped())
            }
            // 轴通道同时更新，供 AXIS_BILLBOARD 等按轴解释朝向的消费者使用。
            particle.axis = direction
            particle.previewAxis = direction
        }
        if (request.pathReachedEndAndDisappears()) {
            // 到达后消失是显式结束模式；走既有的寿命/删除流程，不额外修改 lifetime。
            particle.controler.remove(RemoveReason.LIFECYCLE)
        }
    }

    /**
     * 数据同步需要实现此方法
     *
     * @param emitters 更新的模板发射器
     */
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
