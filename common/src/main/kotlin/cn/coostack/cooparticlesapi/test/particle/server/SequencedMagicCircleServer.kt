package cn.coostack.cooparticlesapi.test.particle.server

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffer
import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.network.particle.SequencedServerParticleGroup
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import cn.coostack.cooparticlesapi.test.particle.client.SequencedMagicCircleClient
import java.util.UUID

class SequencedMagicCircleServer(val bindPlayer: UUID) : SequencedServerParticleGroup(16.0) {
    val maxCount = maxCount()
    var add = false
    var st = 0
    val maxSt = 72
    var stToggle = false
    override fun tick() {
        val player = world!!.getPlayerByUUID(bindPlayer) ?: return
        setPosOnServer(player.position())
        if (st++ > maxSt) {
            if (!stToggle) {
                stToggle = true
                for (i in 0 until maxCount()) {
                    if (i <= 30) {
                        setDisplayed(i, true)
                    } else {
                        setDisplayed(i, false)
                    }
                }
                toggleCurrentCount()
            }
            return
        }
        if (add && serverSequencedParticleCount >= maxCount) {
            add = false
            serverSequencedParticleCount = maxCount
        } else if (!add && serverSequencedParticleCount <= 0) {
            add = true
            serverSequencedParticleCount = 0
        }
        if (add) {
            addMultiple(10)
        } else {
            removeMultiple(10)
        }
    }

    override fun otherPacketArgs(): Map<String, ParticleControlerDataBuffer<out Any>> {
        return mapOf(
            "bind_player" to ParticleControlerDataBuffers.uuid(bindPlayer),
            toggleArgLeastIndex(),
            toggleArgStatus()
        )
    }

    override fun getClientType(): Class<out ControlableParticleGroup>? {
        return SequencedMagicCircleClient::class.java
    }

    override fun maxCount(): Int {
        return 360
    }
}