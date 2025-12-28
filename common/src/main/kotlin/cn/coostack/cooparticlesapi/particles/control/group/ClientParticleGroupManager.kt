package cn.coostack.cooparticlesapi.particles.control.group

import net.minecraft.client.Minecraft
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

@Deprecated("使用ParticleGroupStyle！")
object ClientParticleGroupManager {
    private val visibleControls = ConcurrentHashMap<UUID, ControlableParticleGroup>()

    private val registerBuilders =
        HashMap<Class<out ControlableParticleGroup>, ControlableParticleGroupProvider>()

    fun register(
        type: Class<out ControlableParticleGroup>,
        provider: ControlableParticleGroupProvider
    ) {
        registerBuilders[type] = provider
    }

    fun getBuilder(type: Class<out ControlableParticleGroup>): ControlableParticleGroupProvider? {
        return registerBuilders[type]
    }

    fun getControlGroup(groupId: UUID): ControlableParticleGroup? {
        return visibleControls[groupId]
    }

    fun addVisibleGroup(group: ControlableParticleGroup) {
        visibleControls[group.uuid] = group
    }

    fun removeVisible(id: UUID) {
        visibleControls[id]?.remove()
        visibleControls.remove(id)
    }

    fun clearAllVisible() {
        visibleControls.onEach {
            it.value.canceled = true
        }.clear()
    }

    fun doClientTick() {
        val player = Minecraft.getInstance().player ?: return
        if (player.isDeadOrDying) {
            visibleControls.clear()
            return
        }
        val iterator = visibleControls.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            next.value.tick()
        }
    }

}