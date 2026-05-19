package cn.coostack.cooparticlesapi.annotations.packet

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * # CooPacket 自动注册中心
 *
 * - 扫描所有被 [CooAutoRegister] 标记 且继承自 [CooPacket] 的类
 * - 创建一个空构造样例 (用于探测 id() 与 codec())
 * - 以 [CooPacket.id] 作为索引存储 codec 与构造器
 * - 编码 / 解码均通过 [encode] / [decode] 完成
 */
object CooPacketRegistry {
    private data class Entry(
        val packetClass: Class<out CooPacket>,
        val codec: StreamCodec<FriendlyByteBuf, CooPacket>,
    )

    private val byId = ConcurrentHashMap<ResourceLocation, Entry>()
    private val byClass = ConcurrentHashMap<Class<out CooPacket>, ResourceLocation>()

    private var scanned = false

    /**
     * 注册扫描入口，CooParticlesAPI 主入口会调用
     */
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
                    "CooPacket 自动注册失败: ${packetClass.name}", e
                )
            }
        }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("CooPacket 自动注册完成: 共 $registered 个, 耗时 ${end - start}ms")
    }

    /**
     * 手动注册一个 CooPacket 类型 (一般用户不必直接调用，由 [registerScanner] 触发)
     */
    fun register(packetClass: Class<out CooPacket>) {
        val sample = try {
            packetClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("CooPacket ${packetClass.name} 必须提供空构造函数", e)
        }
        val id = sample.id()
        @Suppress("UNCHECKED_CAST")
        val codec = sample.codec() as StreamCodec<FriendlyByteBuf, CooPacket>
        val existing = byId[id]
        if (existing != null && existing.packetClass != packetClass) {
            throw IllegalStateException("CooPacket ID 冲突: $id 同时被 ${existing.packetClass.name} 和 ${packetClass.name} 使用")
        }
        byId[id] = Entry(packetClass, codec)
        byClass[packetClass] = id
    }

    fun isRegistered(id: ResourceLocation): Boolean = byId.containsKey(id)

    fun isRegistered(packetClass: Class<out CooPacket>): Boolean = byClass.containsKey(packetClass)

    fun idOf(packet: CooPacket): ResourceLocation {
        return byClass[packet::class.java] ?: packet.id()
    }

    /**
     * 把 packet 编码为字节数组 (用于装入信封)
     */
    fun encode(packet: CooPacket): ByteArray {
        val entry = byId[packet.id()]
            ?: throw IllegalStateException(
                "CooPacket 未注册: ${packet::class.java.name} (id=${packet.id()}). " +
                        "请确认该类已加上 @CooAutoRegister 并位于扫描包内"
            )
        val buf = FriendlyByteBuf(Unpooled.buffer())
        entry.codec.encode(buf, packet)
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        return bytes
    }

    /**
     * 根据 ID 与 字节数组解码出 packet
     */
    fun decode(id: ResourceLocation, data: ByteArray): CooPacket? {
        val entry = byId[id] ?: return null
        val buf = FriendlyByteBuf(Unpooled.wrappedBuffer(data))
        return try {
            entry.codec.decode(buf)
        } catch (e: Throwable) {
            CooParticlesConstants.logger.error("CooPacket 解码失败: $id", e)
            null
        }
    }
}
