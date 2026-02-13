package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

@EventBusSubscriber
object ClientEventsListener {
    @SubscribeEvent
    fun onClientWorldRender(e: RenderLevelStageEvent) {
        when (e.stage) {
            RenderLevelStageEvent.Stage.AFTER_ENTITIES -> {
                CooEventBus.call(
                    ClientWorldRenderEvent(
                        Minecraft.getInstance().level ?: return,
                        ClientWorldRenderEvent.RenderStage.AFTER_ENTITY,
                        e.modelViewMatrix,
                        e.projectionMatrix,
                        e.poseStack,
                        (e.levelRenderer as LevelRendererAccessor).renderBuffers().bufferSource(),
                        e.levelRenderer,
                        e.camera,
                        e.partialTick
                    )
                )
            }
        }
    }

}