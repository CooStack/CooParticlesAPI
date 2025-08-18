package cn.coostack.cooparticlesapi.network.buffer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import io.netty.buffer.Unpooled
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class Vec3dControlerBuffer : ParticleControlerDataBuffer<Vec3> {
    companion object {
        @JvmStatic
        val id = ParticleControlerDataBuffer.Id(
            ResourceLocation.fromNamespaceAndPath(
                CooParticlesConstants.MOD_ID, "vec3d"
            )
        )
    }

    override var loadedValue: Vec3? = Vec3.ZERO

    override fun encode(): ByteArray {
        return encode(loadedValue!!)
    }

    override fun decode(buf: ByteArray): Vec3 {
        val wrappedBuffer = Unpooled.wrappedBuffer(buf)
        return Vec3(wrappedBuffer.readDouble(), wrappedBuffer.readDouble(), wrappedBuffer.readDouble())
    }

    override fun getBufferID(): ParticleControlerDataBuffer.Id {
        return id
    }

    override fun encode(value: Vec3): ByteArray {
        val buf = Unpooled.buffer()
        buf.writeDouble(value.x)
        buf.writeDouble(value.y)
        buf.writeDouble(value.z)
        return buf.copy().array()
    }

}