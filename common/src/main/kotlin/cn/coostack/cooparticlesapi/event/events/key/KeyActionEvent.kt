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

/**
 * Fired on client when sending a key action; fired on server when receiving the packet.
 *
 * @param pressTick 按键已按住的 tick 数(长按时每 tick 递增)
 * @param isRelease 是否为松开按键时的触发
 * @param serverSide 是否在服务端触发
 */
class KeyActionEvent(
    player: Player,
    val keyId: ResourceLocation,
    val action: KeyActionType,
    val pressTick: Int,
    val isRelease: Boolean,
    val serverSide: Boolean
) : PlayerEvent(player)
