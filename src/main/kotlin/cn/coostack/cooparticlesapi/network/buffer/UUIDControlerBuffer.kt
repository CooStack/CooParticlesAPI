package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticleAPI
import net.minecraft.util.Identifier
import java.util.UUID

class UUIDControlerBuffer : ParticleControlerDataBuffer<UUID> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            Identifier.of(
                CooParticleAPI.MOD_ID, "uuid"
            )
        )
    }
    override var loadedValue: UUID? = UUID.randomUUID()
    override fun encode(value: UUID): ByteArray {
        return value.toString().toByteArray()
    }

    override fun encode(): ByteArray {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): UUID {
        return UUID.fromString(String(buf))
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

}
