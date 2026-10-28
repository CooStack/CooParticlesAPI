package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent

/** 通过 NeoForge 26.3 的 render-state 与提交事件转发客户端世界渲染回调。 */
@EventBusSubscriber(
    modid = CooParticlesConstants.MOD_ID,
    value = [Dist.CLIENT]
)
object ClientEventsListener {
    /**
     * 缓存本帧 render-state 身份与世界 tick 插值，供后续提交事件校验。
     *
     * @param event 当前客户端 render-state 提取事件
     */
    @SubscribeEvent
    fun onExtractLevelRenderState(event: ExtractLevelRenderStateEvent) {
        renderState = event.renderState
        worldPartialTicks = event.deltaTracker.getGameTimeDeltaPartialTick(false)
    }

    /**
     * 在 NeoForge 26.3 的自定义世界几何提交点转发公共事件。
     *
     * 此 hook 在不透明提交绘制前执行，与 Fabric 的 solid-feature 后置 hook 时序不同。
     *
     * @param event 当前帧的几何提交上下文
     */
    @SubscribeEvent
    fun onSubmitCustomGeometry(event: SubmitCustomGeometryEvent) {
        val state = event.levelRenderState
        if (state !== renderState) return
        renderState = null
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        CooEventBus.call(
            ClientWorldRenderEvent(
                level,
                event.poseStack,
                event.submitNodeCollector,
                client.levelRenderer,
                state.cameraRenderState,
                worldPartialTicks
            )
        )
    }

    /** 仅在 render-state 提取与同帧几何提交之间暂存帧身份。 */
    private var renderState: LevelRenderState? = null

    /** 当前 render-state 提取阶段对应的世界 tick 插值。 */
    private var worldPartialTicks = 0F
}
