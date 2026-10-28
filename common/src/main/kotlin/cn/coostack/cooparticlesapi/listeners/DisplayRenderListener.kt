package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerDisconnectEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent

@EventListener(CooParticlesConstants.MOD_ID, dist = DistType.CLIENT)
object DisplayRenderListener {
    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        val camera = event.camera
        DisplayEntityManager.submit(
            camera.viewRotationMatrix,
            camera.projectionMatrix,
            event.poseStack,
            event.collector,
            event.delta,
            camera,
        )
    }


}
