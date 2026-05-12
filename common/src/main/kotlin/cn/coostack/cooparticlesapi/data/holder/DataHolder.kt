package cn.coostack.cooparticlesapi.data.holder

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.network.packet.server.PacketDataHolderS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.jvm.java

/**
 * # 实体上的 DataHolder。
 *
 * - server 和 client 会各自保存一份数据，不共享同一个 map。
 * - 服务端写入会同步到客户端；客户端也可以写本地值，但下次收到服务端同步时会被服务端值覆盖。
 *
 * - cacheAllToggle = false 时，客户端只覆盖服务端发来的 key，不会删掉客户端本地多出来的数据。
 * - cacheAllToggle = true 时，客户端会完全按服务端快照刷新，包括 key 的数量。
 *
 * # 使用注意事项
 * 1. 你输入的数据（Any）类型必须在CodecHelper有Codec注册， 一般来说支持大多数基本类型
 * 2. 目前没对NBT类型进行处理
 * 3. 如果你需要兼容自己的数据，可以调用[cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register] 对类型进行Codec注册
 */
class DataHolder(private val entity: Entity? = null) {
    private val serverData = ConcurrentHashMap<DataHolderKey<*>, Any>()
    private val clientData = ConcurrentHashMap<DataHolderKey<*>, Any>()

    var cacheAllToggle: Boolean = true

    operator fun <T : Any> set(key: DataHolderKey<T>, value: T) {
        require(CodecHelper.isSupposedType(key.targetType)) {
            "请调用cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register 对${key.targetType.name}进行Codec注册"
        }

        if (isClientSide()) {
            clientData[key] = value
            return
        }
        setServer(key, value)
    }

    fun <T : Any> setServer(key: DataHolderKey<T>, value: T) {
        require(CodecHelper.isSupposedType(key.targetType)) {
            "请调用cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register 对${key.targetType.name}进行Codec注册"
        }
        serverData[key] = value
        sync(false)
    }

    fun <T : Any> setClient(key: DataHolderKey<T>, value: T) {
        require(CodecHelper.isSupposedType(key.targetType)) {
            "请调用cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register 对${key.targetType.name}进行Codec注册"
        }
        clientData[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: DataHolderKey<T>): T? {
        return if (isClientSide()) {
            clientData[key] as? T
        } else {
            serverData[key] as? T
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getServer(key: DataHolderKey<T>): T? {
        return serverData[key] as? T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getClient(key: DataHolderKey<T>): T? {
        return clientData[key] as? T
    }

    fun <T> getOrCreate(key: DataHolderKey<T>, new: Supplier<T>): T {
        return if (isClientSide()) {
            getClientOrCreate(key, new)
        } else {
            getServerOrCreate(key, new)
        }
    }

    fun <T> getServerOrCreate(key: DataHolderKey<T>, new: Supplier<T>): T {
        require(CodecHelper.isSupposedType(key.targetType)) {
            "请调用cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register 对${key.targetType.name}进行Codec注册"
        }
        return if (!has(key)) {
            val res = new.get()
            serverData[key] = res as Any
            sync(false)
            res
        } else {
            @Suppress("UNCHECKED_CAST")
            serverData[key] as T
        }
    }

    fun <T> getClientOrCreate(key: DataHolderKey<T>, new: Supplier<T>): T {
        require(CodecHelper.isSupposedType(key.targetType)) {
            "请调用cn.coostack.cooparticlesapi.annotations.codec.CodecHelper.register 对${key.targetType.name}进行Codec注册"
        }
        return if (!hasClient(key)) {
            val res = new.get()
            clientData[key] = res as Any
            res
        } else {
            @Suppress("UNCHECKED_CAST")
            clientData[key] as T
        }
    }

    fun has(key: DataHolderKey<*>): Boolean {
        return serverData.containsKey(key)
    }

    fun hasClient(key: DataHolderKey<*>): Boolean {
        return clientData.containsKey(key)
    }

    fun has(key: ResourceLocation): Boolean {
        return serverData.keys.any { key == it.id }
    }

    fun hasClient(key: ResourceLocation): Boolean {
        return clientData.keys.any { key == it.id }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> remove(key: DataHolderKey<T>): T? {
        return if (isClientSide()) {
            removeClient(key)
        } else {
            removeServer(key)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> removeServer(key: DataHolderKey<T>): T? {
        val removed = serverData.remove(key)
        sync(false)
        return removed as? T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> removeClient(key: DataHolderKey<T>): T? {
        val removed = clientData.remove(key)
        return removed as? T
    }

    fun removeAllIfId(id: ResourceLocation) {
        serverData.keys.removeIf { it.id == id }
        sync(false)
    }

    fun removeAllClientIfId(id: ResourceLocation) {
        clientData.keys.removeIf { it.id == id }
    }

    fun clearServer() {
        serverData.clear()
        sync(true)
    }

    fun clearClient() {
        clientData.clear()
    }

    fun clearAll() {
        serverData.clear()
        clientData.clear()
    }

    internal fun snapshotServer(): Map<DataHolderKey<*>, Any> {
        return serverData.toMap()
    }

    internal fun applyClientSnapshot(snapshot: Map<DataHolderKey<*>, Any>, fullSync: Boolean) {
        if (cacheAllToggle) {
            clientData.clear()
        }
        snapshot.forEach { (key, value) ->
            clientData[key] = value
        }
    }

    private fun sync(fullSync: Boolean = true) {
        val current = entity ?: return
        if (current.level().isClientSide) return
        val packet = PacketDataHolderS2C.fromEntity(current, this, fullSync)
        CooParticlesServices.SERVER_NETWORK.sendToPlayersTrackingChunk(
            current.level() as ServerLevel,
            current.chunkPosition(),
            packet
        )
    }

    private fun isClientSide(): Boolean {
        return entity?.level()?.isClientSide == true
    }
}
