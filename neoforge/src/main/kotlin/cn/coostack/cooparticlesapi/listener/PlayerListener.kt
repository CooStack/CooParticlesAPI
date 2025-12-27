package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerDeathEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerRespawnEvent
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

@EventBusSubscriber
object PlayerListener {
    @SubscribeEvent
    fun playerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val entity = event.entity ?: return
        val world = entity.level()
        CooEventBus.call(ServerPlayerRespawnEvent(entity, world))
    }

    @SubscribeEvent
    fun playerDeath(event: LivingDeathEvent) {
        val entity = event.entity ?: return
        if (entity !is Player) {
            return
        }
        val world = entity.level()
        val source = event.source
        event.isCanceled = CooEventBus.call(ServerPlayerDeathEvent(entity, world, source)).isCancelled
    }

}