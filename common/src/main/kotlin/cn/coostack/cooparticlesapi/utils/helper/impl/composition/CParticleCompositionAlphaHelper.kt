package cn.coostack.cooparticlesapi.utils.helper.impl.composition

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleTransitionMode
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 在运行时 composition 树中查找 CParticle systems，并播放 system 级 alpha 过渡。
 * 过渡在 GPU 中计算，不会逐个改写粒子实例。
 */
object CParticleCompositionAlphaHelper {
    @JvmStatic
    @JvmOverloads
    fun play(
        composition: ParticleComposition,
        durationTicks: Float,
        alphaCurve: CParticleCurve,
        mode: CParticleTransitionMode = CParticleTransitionMode.HOLD_END,
        restart: Boolean = false,
    ): ParticleComposition {
        forEachSystem(composition) { system ->
            system.playAlphaTransition(
                durationTicks = durationTicks,
                alphaCurve = alphaCurve,
                mode = mode,
                restart = restart,
            )
        }
        return composition
    }

    @JvmStatic
    @JvmOverloads
    fun stop(
        composition: ParticleComposition,
        reset: Boolean = false,
    ): ParticleComposition {
        forEachSystem(composition) { it.stopAlphaTransition(reset) }
        return composition
    }

    private fun forEachSystem(
        composition: ParticleComposition,
        action: (CParticleSystem) -> Unit,
    ) {
        val queue = ArrayDeque<Controlable<*>>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Controlable<*>, Boolean>())
        val visitedSystems = Collections.newSetFromMap(IdentityHashMap<CParticleSystem, Boolean>())
        queue.add(composition)

        while (queue.isNotEmpty()) {
            val controlable = queue.removeFirst()
            if (!visited.add(controlable)) continue

            when (controlable) {
                is ParticleComposition -> {
                    controlable.getCParticleSystems().forEach { system ->
                        if (visitedSystems.add(system)) action(system)
                    }
                    queue.addAll(controlable.getCParticleContainers())
                }

                is ParticleGroupStyle -> {
                    controlable.getCParticleSystems().forEach { system ->
                        if (visitedSystems.add(system)) action(system)
                    }
                    queue.addAll(controlable.getCParticleContainers())
                }

                is ControlableParticleGroup -> {
                    controlable.getCParticleSystems().forEach { system ->
                        if (visitedSystems.add(system)) action(system)
                    }
                    queue.addAll(controlable.getCParticleContainers())
                }
                is CParticleControlable -> {
                    if (controlable.valid && visitedSystems.add(controlable.system)) {
                        action(controlable.system)
                    }
                }
            }
        }
    }
}
