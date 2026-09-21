package cn.coostack.cooparticlesapi.annotations.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

object CooPacketRegistry {
    private data class Entry(
        val packetClass: Class<out CooPacket>,
        val codec: CommonStreamCodec<CooPacket>,
    )

    private val byId = ConcurrentHashMap<ResourceLocation, Entry>()
    private val byClass = ConcurrentHashMap<Class<out CooPacket>, ResourceLocation>()

    private var scanned = false

    fun registerScanner() {
        if (scanned) return
        scanned = true
        val start = System.currentTimeMillis()
        val infos = CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
        var registered = 0
        infos.forEach { info ->
            val clazz = runCatching { info.toClass() }.getOrNull() ?: return@forEach
            if (!CooPacket::class.java.isAssignableFrom(clazz)) return@forEach
            @Suppress("UNCHECKED_CAST")
            val packetClass = clazz as Class<out CooPacket>
            try {
                register(packetClass)
                registered++
            } catch (e: Throwable) {
                CooParticlesConstants.logger.error(
                    "CooPacket auto-register failed: ${packetClass.name}", e
                )
            }
        }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("CooPacket auto-register complete: $registered packets, took ${end - start}ms")
    }

    fun register(packetClass: Class<out CooPacket>) {
        val sample = try {
            packetClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("CooPacket ${packetClass.name} must have a no-arg constructor", e)
        }
        val id = sample.id()
        @Suppress("UNCHECKED_CAST")
        val codec = sample.codec() as CommonStreamCodec<CooPacket>
        val existing = byId[id]
        if (existing != null && existing.packetClass != packetClass) {
            throw IllegalStateException("CooPacket ID conflict: $id is used by both ${existing.packetClass.name} and ${packetClass.name}")
        }
        byId[id] = Entry(packetClass, codec)
        byClass[packetClass] = id
    }

    fun isRegistered(id: ResourceLocation): Boolean = byId.containsKey(id)
    fun isRegistered(packetClass: Class<out CooPacket>): Boolean = byClass.containsKey(packetClass)

    fun idOf(packet: CooPacket): ResourceLocation {
        return byClass[packet::class.java] ?: packet.id()
    }

    fun encode(packet: CooPacket): ByteArray {
        val entry = byId[packet.id()]
            ?: throw IllegalStateException(
                "CooPacket not registered: ${packet::class.java.name} (id=${packet.id()}). " +
                        "Make sure the class has @CooAutoRegister and is in the scan package"
            )
        val buf = PacketByteBuf(Unpooled.buffer())
        entry.codec.encode(buf, packet)
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        return bytes
    }

    fun decode(id: ResourceLocation, data: ByteArray): CooPacket? {
        val entry = byId[id] ?: return null
        val buf = PacketByteBuf(Unpooled.wrappedBuffer(data))
        return try {
            entry.codec.decode(buf)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket decode failed: $id", e)
            null
        }
    }
}
