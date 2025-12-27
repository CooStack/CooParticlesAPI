package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.entities.renderer.TestRenderEntityRenderer
import cn.coostack.cooparticlesapi.listeners.ClientPlayerDeathListener
import com.mojang.authlib.minecraft.client.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

@EventBusSubscriber(value = [Dist.CLIENT], modid = CooParticlesConstants.MOD_ID)
object ClientPlayerListener {
    @SubscribeEvent
    fun onPlayerDeath(event: ScreenEvent.Opening) {
        val screen = event.screen
        // 死亡屏幕出现时判定客户端死亡
        if (screen is DeathScreen) {
            ClientPlayerDeathListener.call()
        }
    }

    @SubscribeEvent
    fun registerEntityRenderer(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(
            CooModEntityTypes.TEST_RENDER.get()
        ) {
            TestRenderEntityRenderer(it)
        }
    }

}