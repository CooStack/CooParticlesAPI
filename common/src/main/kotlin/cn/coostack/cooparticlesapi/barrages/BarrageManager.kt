package cn.coostack.cooparticlesapi.barrages

import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.network.particle.style.ParticleStyleManager
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import com.google.common.collect.ConcurrentHashMultiset
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB

object BarrageManager {
    private val barrages = ConcurrentHashMultiset.create<Barrage>()

    fun collectClipBarrages(world: ServerLevel, box: AABB): List<Barrage> {
        return barrages.filter {
            it.valid && world == it.world && !it.noclip() && (box.contains(it.loc) || box.intersects(it.hitBox.ofBox(it.loc)))
        }.toList()
    }

    fun spawn(barrage: Barrage) {
        spawnOnWorld(barrage)
        barrages.add(barrage)
    }


    fun doTick() {
        val iterator = barrages.iterator()
        while (iterator.hasNext()) {
            val barrier = iterator.next()
            barrier.tick()
            if (!barrier.valid) {
                iterator.remove()
            }
        }
    }

    private fun spawnOnWorld(barrage: Barrage) {
        when (val control = barrage.bindControl) {
            is ServerParticleGroup -> {
                ServerParticleGroupManager.addParticleGroup(
                    control,
                    barrage.loc,
                    barrage.world
                )
            }

            is ParticleGroupStyle -> {
                ParticleStyleManager.spawnStyle(
                    barrage.world,
                    barrage.loc,
                    control
                )
            }

            is RenderEntity -> {
                ServerRenderEntityManager.spawn(
                    control
                )
            }
        }
        barrage.lunch = true
    }
}
