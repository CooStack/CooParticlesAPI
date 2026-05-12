package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.data.cache.ClientEntityCacheManager
import cn.coostack.cooparticlesapi.data.cache.ServerEntityCacheManager
import cn.coostack.cooparticlesapi.data.holder.DataHolderManager
import cn.coostack.cooparticlesapi.event.events.entity.EntityUnloadEvent
import cn.coostack.cooparticlesapi.event.events.entity.client.ClientEntityUnloadEvent
import cn.coostack.cooparticlesapi.event.events.entity.server.ServerEntityUnloadEvent

@EventListener
object EntityListener {
    @EventHandler
    fun onEntityUnloaded(event: ServerEntityUnloadEvent) {
        ServerEntityCacheManager.remove(event.entity)
        DataHolderManager.remove(event.entity)
    }

    @EventHandler
    fun onEntityUnloadedClient(event: ClientEntityUnloadEvent) {
        ClientEntityCacheManager.remove(event.entity)
    }

}