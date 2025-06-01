package cn.coostack.cooparticlesapi.network.particle.emitters.impl

import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.Random
import kotlin.collections.associateBy
import kotlin.math.roundToInt
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost

class PresetLaserEmitters(pos: Vec3d, world: World?) : ClassParticleEmitters(pos, world) {
    var templateData = ControlableParticleData()
    var targetPoint = Vec3d.ZERO
    // 激光
    /**
     * 光柱最大的粗度
     */
    var lineScaleMax = 2f

    /**
     * 光柱最小粗度
     */
    var lineScaleMin = 0.01f

    /**
     * 刚刚生成时线条的粗度 (粒子的大小)
     */
    var lineStartScale = 0.2f

    /**
     * 每个方块距离的粒子个数
     */
    var particleCountPreBlock = 5
        set(value) {
            field = value.coerceAtLeast(1)
        }

    /**
     * 设置粒子恒定生命周期 (-1为不恒定)
     */
    var particleAge = 20

    /**
     * 光柱的存活时长
     */
    var lineMaxTick = 120

    /**
     * 线条开始变大
     */
    var lineStartIncreaseTick = 20

    /**
     * 当线条变大时, 初始速度
     */
    var defaultIncreaseSpeed = 0.1f

    /**
     * 当线条变小时 初始速度
     */
    var defaultDecreaseSpeed = 0.1f

    /**
     * 线条最大速度
     */
    var maxIncreaseSpeed = 2f

    /**
     * 线条变大的加速度
     */
    var increaseAcceleration = 0.1f

    /**
     * 开始变细时 最小的速度
     */
    var maxDecreaseSpeed = 2f

    /**
     * 变小时的加速度
     */
    var decreaseAcceleration = 0.01f

    /**
     * 开始变小的时间
     */
    var lineStartDecreaseTick = 100


    var markDeadWhenArriveMinScale = false
    var markDeadWhenArriveMaxScale = false

    init {
        maxTick = 1
    }

    companion object {
        const val ID = "coo-particles-api-preset-laser-emitters"

        @JvmStatic
        val CODEC = PacketCodec.ofStatic<PacketByteBuf, ParticleEmitters>(
            { buf, data ->
                data as PresetLaserEmitters
                buf.writeVec3d(data.targetPoint)
                encodeBase(data, buf)
                ControlableParticleData.PACKET_CODEC.encode(buf, data.templateData)
                buf.writeFloat(data.lineScaleMax)
                buf.writeFloat(data.lineScaleMin)
                buf.writeFloat(data.lineStartScale)
                buf.writeInt(data.particleCountPreBlock)
                buf.writeInt(data.particleAge)
                buf.writeInt(data.lineMaxTick)
                buf.writeInt(data.lineStartIncreaseTick)
                buf.writeFloat(data.defaultIncreaseSpeed)
                buf.writeFloat(data.defaultDecreaseSpeed)
                buf.writeFloat(data.maxIncreaseSpeed)
                buf.writeFloat(data.increaseAcceleration)
                buf.writeFloat(data.maxDecreaseSpeed)
                buf.writeFloat(data.decreaseAcceleration)
                buf.writeInt(data.lineStartDecreaseTick)
                buf.writeBoolean(data.markDeadWhenArriveMinScale)
                buf.writeBoolean(data.markDeadWhenArriveMaxScale)
            }, {
                val target = it.readVec3d()
                val instance = PresetLaserEmitters(Vec3d.ZERO, null)
                decodeBase(instance, it)
                instance.targetPoint = target
                instance.templateData = ControlableParticleData.PACKET_CODEC.decode(it)
                instance.lineScaleMax = it.readFloat()
                instance.lineScaleMin = it.readFloat()
                instance.lineStartScale = it.readFloat()
                instance.particleCountPreBlock = it.readInt()
                instance.particleAge = it.readInt()
                instance.lineMaxTick = it.readInt()
                instance.lineStartIncreaseTick = it.readInt()
                instance.defaultIncreaseSpeed = it.readFloat()
                instance.defaultDecreaseSpeed = it.readFloat()
                instance.maxIncreaseSpeed = it.readFloat()
                instance.increaseAcceleration = it.readFloat()
                instance.maxDecreaseSpeed = it.readFloat()
                instance.decreaseAcceleration = it.readFloat()
                instance.lineStartDecreaseTick = it.readInt()
                instance.markDeadWhenArriveMinScale = it.readBoolean()
                instance.markDeadWhenArriveMaxScale = it.readBoolean()
                instance
            }
        )
    }

    override fun doTick() {
    }

    val random = Random(System.currentTimeMillis())
    override fun genParticles(): Map<ControlableParticleData, RelativeLocation> {
        val res = kotlin.collections.HashMap<ControlableParticleData, RelativeLocation>()
        res.putAll(
            PointsBuilder()
                .addLine(
                    Vec3d.ZERO,
                    targetPoint,
                    (targetPoint.length() * particleCountPreBlock).roundToInt().coerceAtLeast(1)
                )
                .create().associateBy {
                    templateData.clone()
                }
        )
        return res
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: Vec3d,
        spawnWorld: World
    ) {
        data.textureSheet = ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
        var tick = 0
        var speed = 0f
        data.maxAge = lineMaxTick
        data.size = lineStartScale
        controler.addPreTickAction {
            tick++
            if (particleAge != -1) {
                currentAge = particleAge
            }
            if (tick == lineStartIncreaseTick) {
                speed = defaultIncreaseSpeed
            }
            if (tick > lineStartIncreaseTick && tick <= lineStartDecreaseTick) {
                size = (size + speed).coerceAtMost(lineScaleMax)
                speed = (speed + increaseAcceleration).coerceAtMost(maxIncreaseSpeed)
                if (markDeadWhenArriveMaxScale && size >= lineScaleMax) {
                    markDead()
                }
            }
            if (tick == lineStartDecreaseTick) {
                speed = defaultDecreaseSpeed
            }
            if (tick > lineStartDecreaseTick) {
                size = (size - speed).coerceAtLeast(lineScaleMin)
                speed = (speed + decreaseAcceleration).coerceAtMost(maxDecreaseSpeed)
                if (markDeadWhenArriveMinScale && size <= lineScaleMin) {
                    markDead()
                }
            }
            if (tick >= lineMaxTick) {
                markDead()
            }
        }
    }

    override fun getEmittersID(): String {
        return ID
    }

    override fun update(emitters: ParticleEmitters) {
        super.update(emitters)
        if (emitters !is PresetLaserEmitters) {
            return
        }

        this.templateData = emitters.templateData
        this.targetPoint = emitters.targetPoint
        this.lineScaleMax = emitters.lineScaleMax
        this.lineScaleMin = emitters.lineScaleMin
        this.lineStartScale = emitters.lineStartScale
        this.particleCountPreBlock = emitters.particleCountPreBlock
        this.particleAge = emitters.particleAge
        this.lineMaxTick = emitters.lineMaxTick
        this.lineStartIncreaseTick = emitters.lineStartIncreaseTick
        this.defaultIncreaseSpeed = emitters.defaultIncreaseSpeed
        this.defaultDecreaseSpeed = emitters.defaultDecreaseSpeed
        this.maxIncreaseSpeed = emitters.maxIncreaseSpeed
        this.increaseAcceleration = emitters.increaseAcceleration
        this.maxDecreaseSpeed = emitters.maxDecreaseSpeed
        this.decreaseAcceleration = emitters.decreaseAcceleration
        this.lineStartDecreaseTick = emitters.lineStartDecreaseTick
        this.markDeadWhenArriveMinScale = emitters.markDeadWhenArriveMinScale
        this.markDeadWhenArriveMaxScale = emitters.markDeadWhenArriveMaxScale
    }

    override fun getCodec(): PacketCodec<PacketByteBuf, ParticleEmitters> {
        return CODEC
    }
}