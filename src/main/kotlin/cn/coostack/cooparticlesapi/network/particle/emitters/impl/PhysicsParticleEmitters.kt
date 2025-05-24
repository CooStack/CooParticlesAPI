package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.emitters.PhysicConstant
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.type.EmittersShootTypes
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import com.ezylang.evalex.Expression
import net.minecraft.client.world.ClientWorld
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.Random
import java.util.UUID
import kotlin.math.max
import kotlin.math.pow

class PhysicsParticleEmitters(
    override var pos: Vec3d,
    override var world: World?,
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

    var shootType = EmittersShootTypes.point()

    /**
     * 表达式规则基于 EvalEx-3.5.0项目
     * t生成时间 整数
     * 返回值是基于当前值的偏移量 (offset)
     */
    var evalEmittersXWithT = "0"
    var evalEmittersYWithT = "0"
    var evalEmittersZWithT = "0"

    var offset = Vec3d(0.0, 0.0, 0.0)

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
    var wind: WindDirection = GlobalWindDirection(Vec3d.ZERO)

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
        val CODEC: PacketCodec<RegistryByteBuf, ParticleEmitters> =
            PacketCodec.ofStatic<RegistryByteBuf, ParticleEmitters>(
                { buf, data ->
                    data as PhysicsParticleEmitters
                    buf.writeInt(data.count)
                    buf.writeInt(data.countRandom)
                    buf.writeVec3d(data.pos)
                    buf.writeInt(data.tick)
                    buf.writeInt(data.maxTick)
                    buf.writeInt(data.delay)
                    buf.writeUuid(data.uuid)
                    buf.writeBoolean(data.playing)
                    buf.writeBoolean(data.cancelled)
                    buf.writeDouble(data.gravity)
                    buf.writeDouble(data.airDensity)
                    buf.writeDouble(data.mass)
                    buf.writeString(data.wind.getID())
                    data.wind.getCodec().encode(buf, data.wind)
                    buf.writeString(data.evalEmittersXWithT, 32767)
                    buf.writeString(data.evalEmittersYWithT, 32767)
                    buf.writeString(data.evalEmittersZWithT, 32767)
                    buf.writeVec3d(data.offset)
                    buf.writeString(data.shootType.getID(), 32767)
                    data.shootType.getCodec().encode(buf, data.shootType)
                    ControlableParticleData.PACKET_CODEC.encode(
                        buf,
                        data.templateData
                    )
                }, {
                    val count = it.readInt()
                    val countRandom = it.readInt()
                    val pos = it.readVec3d()
                    val tick = it.readInt()
                    val maxTick = it.readInt()
                    val delay = it.readInt()
                    val uuid = it.readUuid()
                    val play = it.readBoolean()
                    val cancelled = it.readBoolean()
                    val gravity = it.readDouble()
                    val airDensity = it.readDouble()
                    val mass = it.readDouble()
                    val windID = it.readString()
                    val from = WindDirections.getCodecFromID(windID)
                    val wind = from.decode(it)
                    val xE = it.readString(32767)
                    val yE = it.readString(32767)
                    val zE = it.readString(32767)
                    val offset = it.readVec3d()
                    val typeID = it.readString(32767)
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
        if (world?.isClient == false) {
            ParticleEmittersManager.updateEmitters(this)
        }
    }

    override fun tick() {
        if (cancelled || !playing) {
            return
        }
        if (tick++ >= maxTick && maxTick != -1) {
            stop()
        }
        offset = Vec3d(
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
        if (!world!!.isClient) {
            return
        }

        if (tick % max(1, delay) == 0) {
            // 执行粒子变更操作
            // 生成新粒子
            spawnParticle()
        }
    }

    override fun spawnParticle() {
        if (!world!!.isClient) {
            return
        }
        val world = world as ClientWorld
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
            ).normalize().multiply(newData.speed)
            newData.velocity = v
            spawnParticle(world, it, newData)
        }
    }

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
            this.textureSheet = data.textureSheet
            this.particleAlpha = data.alpha
        }
        control.addPreTickAction {
            // 模拟粒子运动 速度
            teleportTo(
                this.pos.add(data.velocity)
            )
            updatePhysics(this.pos, data)
            if (currentAge++ >= maxAge) {
                markDead()
            }
        }
        displayer.display(pos, world)
    }

    private fun updatePhysics(pos: Vec3d, data: ControlableParticleData) {
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

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is PhysicsParticleEmitters) return
        this.tick = emitters.tick
        this.maxTick = emitters.maxTick
        this.delay = emitters.delay
        this.playing = emitters.playing
        this.cancelled = emitters.cancelled
        this.templateData = emitters.templateData
        this.pos = emitters.pos
    }

    override fun getCodec(): PacketCodec<RegistryByteBuf, ParticleEmitters> {
        return CODEC
    }
}