package cn.coostack.cooparticlesapi.network.packet.server

import net.minecraft.network.FriendlyByteBuf
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketParticleCompositionCodecTest {
    @Test
    fun `codec preserves recreate lifecycle flag`() {
        val packet = PacketParticleCompositionS2C(
            UUID.fromString("00000000-0000-0000-0000-000000000123"),
            "test.Composition",
            byteArrayOf(1, 2, 3),
        ).apply {
            recreate = true
        }
        val byteBuf = Class.forName("io.netty.buffer.Unpooled")
            .getMethod("buffer")
            .invoke(null)
        val buffer = FriendlyByteBuf::class.java.constructors
            .single { it.parameterCount == 1 }
            .newInstance(byteBuf) as FriendlyByteBuf

        try {
            PacketParticleCompositionS2C.CODEC.encode(buffer, packet)
            val decoded = PacketParticleCompositionS2C.CODEC.decode(buffer)

            assertEquals(packet.uuid, decoded.uuid)
            assertEquals(packet.type, decoded.type)
            assertContentEquals(packet.data, decoded.data)
            assertTrue(decoded.recreate)
        } finally {
            buffer::class.java.getMethod("release").invoke(buffer)
        }
    }
}
