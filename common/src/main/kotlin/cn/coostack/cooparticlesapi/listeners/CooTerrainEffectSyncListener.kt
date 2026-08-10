package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerLoggedInEvent
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager
import net.minecraft.server.level.ServerPlayer

/** 玩家登录时补发当前维度的持久方块效果组。 */
@EventListener
object CooTerrainEffectSyncListener {
    @EventHandler
    fun onPlayerLoggedIn(event: PlayerLoggedInEvent) {
        val player = event.player as? ServerPlayer ?: return
        CooTerrainEffectManager.syncTo(player)
    }
}
