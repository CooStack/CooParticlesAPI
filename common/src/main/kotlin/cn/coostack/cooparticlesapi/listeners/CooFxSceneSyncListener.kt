package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.coofx.server.CooFxSceneManager
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerLoggedInEvent
import net.minecraft.server.level.ServerPlayer

/** 玩家登录时补发当前维度和可见范围内的长期 CooFX scene。 */
@EventListener
object CooFxSceneSyncListener {
    @EventHandler
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        val player = event.player as? ServerPlayer ?: return
        CooFxSceneManager.syncTo(player)
    }
}
