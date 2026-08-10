package cn.coostack.cooparticlesapi.listener

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPostTickEvent
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPreTickEvent
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber
object WorldListener {
    @SubscribeEvent
    fun tickWorldPost(event: LevelTickEvent.Post) {
        if (event.level.isClientSide) return
        val level = event.level as ServerLevel
        val event = ServerWorldPostTickEvent(level, level.server)
        CooEventBus.call(event)
    }

    @SubscribeEvent
    fun tickWorldPre(event: LevelTickEvent.Pre) {
        if (event.level.isClientSide) return
        val level = event.level as ServerLevel
        val event = ServerWorldPreTickEvent(level, level.server)
        CooEventBus.call(event)
    }

}
