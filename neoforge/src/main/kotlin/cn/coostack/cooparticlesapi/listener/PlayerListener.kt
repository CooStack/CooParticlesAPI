package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadType
import cn.coostack.cooparticlesapi.event.events.entity.EntityPrePlaceBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerDeathEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerRespawnEvent
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

@EventBusSubscriber
object PlayerListener {
    @SubscribeEvent
    fun playerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val entity = event.entity ?: return
        val world = entity.level()
        if (!world.isClientSide) {
            ParticleCompositionManager.clearVisibleFor(entity)
            CooEventBus.call(ServerPlayerRespawnEvent(entity, world))
        }
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

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val cooEvent = EntityPrePlaceBlockEvent(
            event.entity,
            event.level,
            event.pos,
            event.hitVec.direction
        )
        if (CooEventBus.call(cooEvent).isCancelled) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    @SubscribeEvent
    fun playerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val entity = event.entity
        CooEventBus.call(
            cn.coostack.cooparticlesapi.event.events.entity.player.PlayerDisconnectEvent(entity, EntityUnloadType.QUIT)
        )
    }
}
