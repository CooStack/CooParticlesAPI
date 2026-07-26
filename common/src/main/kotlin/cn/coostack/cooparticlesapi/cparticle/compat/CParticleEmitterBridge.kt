package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3

/**
 * # CParticleEmitterBridge — "Emitter 是特定的 Data" 的落地
 *
 * 发射器 (客户端) 生成粒子时把 [ControlableParticleData] 直接写入 GPU 粒子系统:
 * - 每个发射器独占一个 SIMULATED 系统 (uuid 命名, 空置自动回收)
 * - 发射器内建物理 (gravity / airDensity / 全局风) 自动映射为 GPU 力场,
 *   与 `updatePhysics` 公式一致
 * - 附加运动通过 [ClassParticleEmitters.cparticleForces] 声明
 *   (内置 ParticleCommand 可用 [CParticleForce.fromCommand] 直接转换)
 * - effect 的贴图通过粒子引擎 SpriteSet 解析, 外观与原路径一致
 *
 * 需要 singleParticleAction、碰撞与事件、singleParticleDeathAction 重生，
 * 或非全局/relative 风场的粒子，应通过
 * [ClassParticleEmitters.shouldUseCParticleSystem] 留在 CPU 路径.
 */
object CParticleEmitterBridge {

    /**
     * 尝试把一个粒子交给 GPU 系统.
     * @return false = 能力不足或 GPU 粒子池已满，调用方回退原版粒子路径
     */
    @JvmStatic
    fun trySpawn(
        emitter: ClassParticleEmitters,
        world: ClientLevel,
        pos: Vec3,
        data: ControlableParticleData,
    ): Boolean {
        if (!CParticleSystemManager.enabled) return false
        CParticleCapabilities.detect()
        if (!CParticleCapabilities.instancingSupported) return false

        val layer = CParticleRenderLayer.fromSheetName(data.getTextureSheet().toString())
        val system = CParticleSystemManager.getOrCreateSystem(
            "emitter/${emitter.uuid}/${layer.name.lowercase()}",
            emitter.cparticleCapacity,
            layer,
            CParticleSystemMode.SIMULATED,
            autoReleaseWhenEmpty = true,
        )
        system.setOriginIfEmpty(emitter.pos)

        // 力场每 tick 与发射器状态同步一次
        if (system.forcesSyncTick != emitter.tick) {
            system.forcesSyncTick = emitter.tick
            syncForces(system.forces, emitter)
        }
        if (system.store.isFull()) return false

        system.speedLimit = data.speedLimit.toFloat()
        // 粒子生成时已按 data.visibleRange 逐粒子剔除过; 整池剔除范围取最大见过的值
        if (data.visibleRange.toDouble() > system.visibleRange) {
            system.visibleRange = data.visibleRange.toDouble()
        }

        val p = CParticle.from(data)
        p.updateMode = emitter.cparticleUpdateMode(data)
        p.pos = pos
        val uv = CParticleSprites.resolveEffect(data.effect, data.age, data.maxAge)
        return system.spawn(p, uv) >= 0
    }

    private fun syncForces(target: MutableList<CParticleForce>, emitter: ClassParticleEmitters) {
        target.clear()
        // 与 ClassParticleEmitters.updatePhysics 相同的三项内建物理
        if (emitter.gravity != 0.0) {
            target.add(CParticleForce.Gravity(emitter.gravity))
        }
        if (emitter.airDensity > 0.0) {
            target.add(CParticleForce.EnvDrag(emitter.airDensity))
        }
        val wind = emitter.wind
        if (wind is GlobalWindDirection && !wind.relative && wind.direction.lengthSqr() > 1e-12) {
            target.add(CParticleForce.Wind({ wind.direction }, emitter.airDensity.coerceAtLeast(1e-4)))
        }
        // 用户声明的附加力场
        for (force in emitter.cparticleForces()) {
            if (target.size >= CParticleForce.MAX_FORCES) break
            target.add(force)
        }
    }
}
