package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.mixin.ParticleEngineAccessor
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.group.ClientParticleGroupManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import net.minecraft.client.Minecraft

object ClientPlayerDeathListener {
    val minecraft: Minecraft
        get() = Minecraft.getInstance()

    fun call() {
        // 清除所有粒子
        val particleEngine = minecraft.particleEngine
        val accessor = particleEngine as ParticleEngineAccessor

        accessor.particles.clear()
        accessor.particlesToAdd.clear()
        accessor.trackedParticleCounts.clear()
        accessor.trackingEmitters.clear()

        ParticleEmittersManager.clearAllVisible()
        ParticleStyleManager.clearAllVisible()
        ClientParticleGroupManager.clearAllVisible()
        ParticleCompositionManager.clearClient()
        ControlParticleManager.clearClient()
        ClientRenderEntityManager.clear()
    }

}
