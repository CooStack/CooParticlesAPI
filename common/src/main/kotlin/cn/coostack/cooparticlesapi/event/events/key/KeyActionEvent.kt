package cn.coostack.cooparticlesapi.event.events.key

import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

enum class KeyActionType(val id: Int) {
    SINGLE_CLICK(0),
    DOUBLE_CLICK(1),
    LONG_PRESS(2);

    companion object {
        fun fromId(id: Int): KeyActionType {
            return entries.firstOrNull { it.id == id } ?: SINGLE_CLICK
        }
    }
}

data class KeyActionData<K>(
    val keyId: K,
    val actions: List<KeyActionType>,
    val pressTick: Int,
    val released: Boolean
) {
    init {
        require(actions.isNotEmpty()) { "key action entry must contain at least one action: $keyId" }
    }
}

class KeyActionBatch<K>(entries: List<KeyActionData<K>>) {
    private val entriesByKey = LinkedHashMap<K, KeyActionData<K>>()

    init {
        entries.forEach(::merge)
    }

    val entries: List<KeyActionData<K>>
        get() = entriesByKey.values.toList()

    fun getKeys(): List<K> {
        return entriesByKey.keys.toList()
    }

    fun getAction(keyId: K): List<KeyActionType> {
        return entriesByKey[keyId]?.actions ?: emptyList()
    }

    fun getPressTick(keyId: K): Int {
        return entriesByKey[keyId]?.pressTick ?: 0
    }

    fun isReleased(keyId: K): Boolean {
        return entriesByKey[keyId]?.released ?: false
    }

    fun isSingleClick(keyId: K): Boolean {
        return KeyActionType.SINGLE_CLICK in getAction(keyId)
    }

    fun isDoubleClick(keyId: K): Boolean {
        return KeyActionType.DOUBLE_CLICK in getAction(keyId)
    }

    fun isLongPress(keyId: K): Boolean {
        return KeyActionType.LONG_PRESS in getAction(keyId)
    }

    fun isEmpty(): Boolean {
        return entriesByKey.isEmpty()
    }

    private fun merge(entry: KeyActionData<K>) {
        val current = entriesByKey[entry.keyId]
        if (current == null) {
            entriesByKey[entry.keyId] = entry
            return
        }
        entriesByKey[entry.keyId] = KeyActionData(
            keyId = entry.keyId,
            actions = current.actions + entry.actions,
            pressTick = maxOf(current.pressTick, entry.pressTick),
            released = current.released || entry.released
        )
    }
}

/**
 * Fired on client when sending a key action; fired on server when receiving the packet.
 *
 * @param serverSide 是否在服务端触发
 */
class KeyActionEvent(
    player: Player,
    val keyActions: KeyActionBatch<ResourceLocation>,
    val serverSide: Boolean
) : PlayerEvent(player) {
    constructor(player: Player, entries: List<KeyActionData<ResourceLocation>>, serverSide: Boolean) : this(
        player,
        KeyActionBatch(entries),
        serverSide
    )

    fun getKeys(): List<ResourceLocation> {
        return keyActions.getKeys()
    }

    fun getAction(keyId: ResourceLocation): List<KeyActionType> {
        return keyActions.getAction(keyId)
    }

    fun getPressTick(keyId: ResourceLocation): Int {
        return keyActions.getPressTick(keyId)
    }

    fun isReleased(keyId: ResourceLocation): Boolean {
        return keyActions.isReleased(keyId)
    }

    fun isSingleClick(keyId: ResourceLocation): Boolean {
        return keyActions.isSingleClick(keyId)
    }

    fun isDoubleClick(keyId: ResourceLocation): Boolean {
        return keyActions.isDoubleClick(keyId)
    }

    fun isLongPress(keyId: ResourceLocation): Boolean {
        return keyActions.isLongPress(keyId)
    }
}
