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
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) {
            return
        }
        val camera = event.camera
        val view = event.viewMatrix
        val proj = event.projectMatrix
        val pose = event.poseStack
        val delta = event.delta
        val buffer = event.buffer
        DisplayEntityManager.render(view, proj, pose, buffer, delta, camera)
    }


}
