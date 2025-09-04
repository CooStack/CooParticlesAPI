package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.CooParticlesAPIClient.initShaderPrograms
import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager.renderTick
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object CooParticlesAPINeoClientListener {
    @SubscribeEvent
    fun tickClient(event: LevelTickEvent.Post) {
        if (!event.level.isClientSide) {
            return
        }
        CooParticlesAPIClient.tickClient(event.level as ClientLevel)
    }

    @SubscribeEvent
    fun onDisconnect(event: PlayerEvent.PlayerLoggedOutEvent) {
        CooParticlesAPIClient.onDisconnect()
    }

    @SubscribeEvent
    fun onWorldChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        CooParticlesAPIClient.afterClientWorldChange()
    }

//    @SubscribeEvent
//    fun onRender(event: RenderLevelStageEvent) {
//        if (event.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return
//        val level = Minecraft.getInstance().level ?: return
//
//        initShaderPrograms()
//        val shouldTick: Boolean = level.tickRateManager().runsNormally()
//        val tickDelta: Float = event.partialTick.getGameTimeDeltaPartialTick(!shouldTick)
//        renderTick(tickDelta, event.modelViewMatrix, event.projectionMatrix)
//    }

}