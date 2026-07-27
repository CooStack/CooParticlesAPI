package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureResolver
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3

/**
 * # CParticleEmitterBridge
 *
 * 发射器在客户端生成粒子时，把当前 [ControlableParticleData] 转成独立的 GPU 实例：
 * - 同一 emitter、渲染层和纹理 binding 共享 SIMULATED 系统；不同 binding 自动拆分
 * - 发射器内建物理 (gravity / airDensity / 全局风) 自动映射为 GPU 力场,
 *   与 `updatePhysics` 公式一致
 * - 附加运动通过 [ClassParticleEmitters.cparticleForces] 声明
 *   (内置 ParticleCommand 可用 [CParticleForce.fromCommand] 直接转换)
 * - 每份 data 自己决定显式纹理来源；未指定时使用它自己的 effect SpriteSet
 *
 * 需要 singleParticleAction、碰撞与事件、singleParticleDeathAction 重生，
 * 或非全局/relative 风场的粒子，应继续使用普通 [ControlableParticleData]。
 */
object CParticleEmitterBridge {

    /**
     * 尝试把一个粒子交给 GPU 系统.
     *
     * Example: 支持 GPU 时生成粒子；达到全局上限时直接丢弃本次生成请求。
     * Forbidden: 容量不足不能返回 `false`，否则调用方会生成 CPU 粒子。
     *
     * @param emitter 当前客户端发射器
     * @param world 当前客户端世界
     * @param pos 粒子的生成坐标
     * @param data 粒子数据
     * @return GPU 路径已处理时返回 `true`；能力或纹理不支持时返回 `false`
     */
    @JvmStatic
    fun trySpawn(
        emitter: ClassParticleEmitters,
        world: ClientLevel,
        pos: Vec3,
        data: ControlableCParticleData,
    ): Boolean {
        if (!CParticleSystemManager.enabled) return false
        CParticleCapabilities.detect()
        if (!CParticleCapabilities.instancingSupported) return false

        val p = CParticle.from(data)
        p.pos = pos
        val source = p.effectiveTextureSource()
        val resolved = CParticleTextureResolver.resolve(source, pos)
        if (!resolved.isValid) return true
        if (!CParticleSystemManager.hasAvailableParticleCapacity()) return true
        val layer = CParticleRenderLayer.fromSheetName(data.getTextureSheet().toString())
        val system = findAvailableSystem(emitter, layer, resolved.bindingKey)
        system.setOriginIfEmpty(emitter.pos)

        // 力场每 tick 与发射器状态同步一次
        if (system.forcesSyncTick != emitter.tick) {
            system.forcesSyncTick = emitter.tick
            syncForces(system.forces, emitter)
        }
        // 粒子生成时已按 data.visibleRange 逐粒子剔除过; 整池剔除范围取最大见过的值
        if (data.visibleRange.toDouble() > system.visibleRange) {
            system.visibleRange = data.visibleRange.toDouble()
        }

        system.spawnResolved(
            p,
            resolved,
            randomQuarterUv = (source as? CParticleTextureSource.Block)?.randomCrop == true,
        )
        return true
    }

    /**
     * 返回仍有空槽位的 emitter system；已有分段写满时创建下一段。
     *
     * Example: `segment 0` 写满后返回同一 emitter 的 `segment 1`。
     * Forbidden: 该方法只负责 system 分段，不能绕过全局粒子上限检查。
     *
     * @param emitter 当前客户端发射器
     * @param layer 粒子的渲染层
     * @param textureBindingKey 本批次使用的主纹理绑定
     * @return 一个仍可写入的 SIMULATED system
     */
    private fun findAvailableSystem(
        emitter: ClassParticleEmitters,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
    ): CParticleSystem {
        val baseName = "emitter/${emitter.uuid}/${layer.name.lowercase()}"
        val segmentCapacity = DEFAULT_SEGMENT_CAPACITY.coerceAtMost(CParticleSystemManager.particleCountLimit)
        var segment = 0
        while (true) {
            val name = if (segment == 0) baseName else "$baseName/$segment"
            val existing = CParticleSystemManager.getSystem(
                name,
                CParticleSystemMode.SIMULATED,
                layer,
                textureBindingKey,
            )
            if (existing == null || !existing.store.isFull()) {
                return existing ?: CParticleSystemManager.getOrCreateSystem(
                    name,
                    segmentCapacity,
                    layer,
                    CParticleSystemMode.SIMULATED,
                    textureBindingKey,
                    autoReleaseWhenEmpty = true,
                )
            }
            segment++
        }
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

    private const val DEFAULT_SEGMENT_CAPACITY = 327670
}
