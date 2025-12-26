package cn.coostack.cooparticlesapi.test.options.particle.server

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.ServerParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.test.options.particle.client.TestGroupClient
import net.minecraft.server.level.ServerPlayer
import java.util.*

class TestParticleGroup(private val bindPlayerUUID: UUID) : ServerParticleGroup(16.0) {
    override fun tick() {
        val bindPlayer = world!!.getPlayerByUUID(bindPlayerUUID) ?: let {
            kill()
            return
        }
        withPlayerStats(bindPlayer as ServerPlayer)
        setPosOnServer(bindPlayer.eyePosition)
    }

    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf(
            "bindUUID" to ParticleControlerDataBuffers.uuid(bindPlayerUUID)
        )
    }

    override fun getClientType(): Class<out ControlableParticleGroup> {
        return TestGroupClient::class.java
    }
}