package cn.coostack.cooparticlesapi.test.particle.server

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroupManager
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.test.particle.client.ScaleCircleGroupClient
import net.minecraft.server.level.ServerPlayer
import java.util.*

class ScaleCircleGroupServer(private val bindPlayerUUID: UUID, visibleRange: Double = 32.0) :
    ServerParticleGroup(visibleRange) {
    private var anTick = 0
    private var anMaxTick = 30

    init {
        maxTick = 120
        tick = 0
    }

    override fun tick() {
        val bindPlayer = world!!.getPlayerByUUID(bindPlayerUUID) ?: let {
            kill()
            return
        }
        doTickAlive()
        if (anTick++ >= anMaxTick) {
            anTick = anMaxTick
        }
        setPosOnServer(bindPlayer.position())
        withPlayerStats(bindPlayer as ServerPlayer)
    }

    override fun onTickAliveDeath() {
    }

    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf(
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayerUUID),
            "an_tick" to ParticleControlerDataBuffers.int(anTick),
            "tick" to ParticleControlerDataBuffers.int(tick),
            "max_tick" to ParticleControlerDataBuffers.int(maxTick)
        )
    }

    override fun getClientType(): Class<out ControlableParticleGroup> {
        return ScaleCircleGroupClient::class.java
    }

}