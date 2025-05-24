package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
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

class SimpleParticleEmitters(
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

    val random = Random(System.currentTimeMillis())

    /**
     * 表达式规则基于 EvalEx-3.5.0项目
     * t生成时间 整数
     * 返回值是基于当前值的偏移量 (offset)
     */
    var evalEmittersXWithT = "0"
    var evalEmittersYWithT = "0"
    var evalEmittersZWithT = "0"
    var shootType = EmittersShootTypes.point()
    private var bufferX = Expression(evalEmittersXWithT)
    private var bufferY = Expression(evalEmittersYWithT)
    private var bufferZ = Expression(evalEmittersZWithT)

    fun setup() {
        bufferX = Expression(evalEmittersXWithT)
        bufferY = Expression(evalEmittersYWithT)
        bufferZ = Expression(evalEmittersZWithT)
    }

    var offset = Vec3d(0.0, 0.0, 0.0)

    /**
     * 每tick生成粒子个数
     */
    var count = 1

    /**
     * 每tick实际生成的粒子个数会受此影响 随机范围(0 .. countRandom)
     */
    var countRandom = 0

    companion object {
        val ID = "simple-emitters"
        val CODEC: PacketCodec<RegistryByteBuf, ParticleEmitters> =
            PacketCodec.ofStatic<RegistryByteBuf, ParticleEmitters>(
                { buf, data ->
                    data as SimpleParticleEmitters
                    buf.writeInt(data.count)
                    buf.writeInt(data.countRandom)
                    buf.writeVec3d(data.pos)
                    buf.writeInt(data.tick)
                    buf.writeInt(data.maxTick)
                    buf.writeInt(data.delay)
                    buf.writeUuid(data.uuid)
                    buf.writeBoolean(data.playing)
                    buf.writeBoolean(data.cancelled)
                    buf.writeString(data.evalEmittersXWithT)
                    buf.writeString(data.evalEmittersYWithT)
                    buf.writeString(data.evalEmittersZWithT)
                    buf.writeVec3d(data.offset)
                    buf.writeString(data.shootType.getID())
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
                    val xE = it.readString()
                    val yE = it.readString()
                    val zE = it.readString()
                    val offset = it.readVec3d()
                    val typeID = it.readString()
                    val codec = EmittersShootTypes.fromID(typeID)!!
                    val type = codec.decode(it)
                    val templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                    SimpleParticleEmitters(pos, null, templateData).apply {
                        this.count = count
                        this.countRandom = countRandom
                        this.tick = tick
                        this.shootType = type
                        this.maxTick = maxTick
                        this.delay = delay
                        this.uuid = uuid
                        this.playing = play
                        this.cancelled = cancelled
                        this.evalEmittersXWithT = xE
                        this.evalEmittersYWithT = yE
                        this.evalEmittersZWithT = zE
                        this.offset = offset
                        setup()
                    }
                }
            )
    }

    override fun start() {
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
        world ?: return
        offset = Vec3d(
            Expression(evalEmittersXWithT)
                .with("t", tick)
                .evaluate().numberValue.toDouble(),
            Expression(evalEmittersYWithT)
                .with("t", tick)
                .evaluate().numberValue.toDouble(),
            Expression(evalEmittersZWithT)
                .with("t", tick)
                .evaluate().numberValue.toDouble()
        )

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
            val v = shootType.getDefaultDirection(newData.velocity, tick, it, currentPos)
                .normalize().multiply(newData.speed)
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
            if (currentAge++ >= maxAge) {
                markDead()
            }
        }
        displayer.display(pos, world)
    }

    override fun update(emitters: ParticleEmitters) {
        if (emitters !is SimpleParticleEmitters) return
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