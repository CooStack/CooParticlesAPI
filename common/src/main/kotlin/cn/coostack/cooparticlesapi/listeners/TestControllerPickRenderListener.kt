package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient

@EventListener(CooParticlesConstants.MOD_ID, dist = DistType.CLIENT)
object TestControllerPickRenderListener {
    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        TestControllerPickClient.render(event)
    }
}
