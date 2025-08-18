package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class UUIDControlerBuffer : ParticleControlerDataBuffer<UUID> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "uuid"
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
