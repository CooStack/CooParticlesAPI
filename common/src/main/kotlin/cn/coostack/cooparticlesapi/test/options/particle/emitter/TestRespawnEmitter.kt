package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleDeathCommand
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/** 用同一死亡指令演示四种 CPU/GPU 转换；默认 GPU 到 GPU，后继只生成一代。 */
@CooAutoRegister
class TestRespawnEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {
    /** 首代是否使用 GPU data，可与 [gpuChild] 独立设置。 */
    @CodecField
    var gpuSource = true

    /** 后继是否使用 GPU data；设为 false 可验证 GPU 到普通粒子的转换。 */
    @CodecField
    var gpuChild = true

    /** 后继相对死亡位置的世界空间偏移，单位为方块。 */
    @CodecField
    var childOffset = Vec3.ZERO

    /** 后继模板之一；普通粒子可在 singleParticleAction 中继续增加行为。 */
    @CodecField
    var cpuChildTemplate = ControlableParticleData().apply {
        maxAge = 40
        size = 0.3F
        color = Vector3f(0.3F, 0.8F, 1F)
    }

    /** 同一配置入口的 GPU 模板，额外支持 GPU 生命周期曲线。 */
    @CodecField
    var gpuChildTemplate = ControlableCParticleData().apply {
        maxAge = 40
        size = 0.3F
        color = Vector3f(0.3F, 0.8F, 1F)
        alphaCurve = CParticleCurve.linear(1F, 0F)
    }

    init {
        deathCommand = ParticleDeathCommand {
            val template = if (gpuChild) gpuChildTemplate else cpuChildTemplate
            listOf(respawnAtDeath(template, offset = childOffset, inheritVelocity = 0.25) {
                velocity = Vec3(0.0, 0.05, 0.0)
                sign = 1
            })
        }
    }

    override fun doTick() {
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        return listOf(
            (if (gpuSource) ControlableCParticleData() else ControlableParticleData()).apply {
                velocity = Vec3(0.0, 0.0, 0.15)
                maxAge = 20
            } to RelativeLocation(),
        )
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float
    ) {
    }
}
