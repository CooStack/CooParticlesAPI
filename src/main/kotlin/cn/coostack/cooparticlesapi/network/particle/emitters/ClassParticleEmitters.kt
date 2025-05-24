package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirection
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.WindDirections
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.CROSS_SECTIONAL_AREA
import cn.coostack.cooparticlesapi.network.particle.emitters.impl.PhysicsParticleEmitters.Companion.DRAG_COEFFICIENT
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.world.ClientWorld
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
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

    companion object {
        fun encodeBase(data: ClassParticleEmitters, buf: RegistryByteBuf) {
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
        fun decodeBase(container: ClassParticleEmitters, buf: RegistryByteBuf) {
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
        if (tick++ >= maxTick && maxTick != -1) {
            stop()
        }

        world ?: return
        doTick()
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
            this.textureSheet = data.textureSheet
            this.particleAlpha = data.alpha
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
    }
}