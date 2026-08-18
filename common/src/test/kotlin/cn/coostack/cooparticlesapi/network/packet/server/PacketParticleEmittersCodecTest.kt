package cn.coostack.cooparticlesapi.network.packet.server

import net.minecraft.network.FriendlyByteBuf
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PacketParticleEmittersCodecTest {
    @Test
    fun `codec preserves emitter lifecycle operation and uuid`() {
        PacketParticleEmittersS2C.PacketType.entries.forEach { type ->
            val data = if (type == PacketParticleEmittersS2C.PacketType.REMOVE) {
                ByteArray(0)
            } else {
                byteArrayOf(1, 2, 3)
            }
            val packet = PacketParticleEmittersS2C(
                "test.emitter",
                UUID.fromString("00000000-0000-0000-0000-000000000456"),
                data,
                type,
            )
            val byteBuf = Class.forName("io.netty.buffer.Unpooled")
                .getMethod("buffer")
                .invoke(null)
            val buffer = FriendlyByteBuf::class.java.constructors
                .single { it.parameterCount == 1 }
                .newInstance(byteBuf) as FriendlyByteBuf

            try {
                PacketParticleEmittersS2C.CODEC.encode(buffer, packet)
                val decoded = PacketParticleEmittersS2C.CODEC.decode(buffer)

                assertEquals(packet.emitterID, decoded.emitterID)
                assertEquals(packet.emitterUUID, decoded.emitterUUID)
                assertContentEquals(packet.emitterData, decoded.emitterData)
                assertEquals(packet.type, decoded.type)
            } finally {
                buffer::class.java.getMethod("release").invoke(buffer)
            }
        }
    }
}
