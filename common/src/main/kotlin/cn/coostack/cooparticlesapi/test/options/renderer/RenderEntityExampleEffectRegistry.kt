package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseBlackHoleLensEntity
import cn.coostack.cooparticlesapi.test.options.renderer.cases.CaseBlackHoleLensRenderRequest
import cn.coostack.cooparticlesapi.test.options.renderer.combat.CombatExplosionEntity
import cn.coostack.cooparticlesapi.test.options.renderer.combat.CombatExplosionShockwaveRequest

object RenderEntityExampleEffectRegistry {
    private var initialized = false

    fun initOnClient() {
        if (initialized) {
            return
        }
        initialized = true
        RenderEffectRegistry.register(TestBlackHoleEntity.FRAME_POST_EFFECT) { effects ->
            TestBlackHoleEntity.renderRequests(payloads<TestBlackHoleRenderRequest>(effects))
        }
        RenderEffectRegistry.register(TestAccretionDiskEntity.FRAME_POST_EFFECT) { effects ->
            TestAccretionDiskEntity.renderRequests(payloads<TestAccretionDiskRenderRequest>(effects))
        }
        RenderEffectRegistry.register(CaseBlackHoleLensEntity.FRAME_POST_EFFECT) { effects ->
            CaseBlackHoleLensEntity.renderRequests(payloads<CaseBlackHoleLensRenderRequest>(effects))
        }
        RenderEffectRegistry.register(CombatExplosionEntity.SHOCKWAVE_EFFECT) { effects ->
            CombatExplosionEntity.renderRequests(payloads<CombatExplosionShockwaveRequest>(effects))
        }
    }

    private inline fun <reified T> payloads(effects: List<RenderEffectDescriptor>): List<T> {
        return effects.mapNotNull { it.payload as? T }
    }
}
