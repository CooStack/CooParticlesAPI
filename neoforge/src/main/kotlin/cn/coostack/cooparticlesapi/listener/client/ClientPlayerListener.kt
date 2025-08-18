package cn.coostack.cooparticlesapi.listener.client

import cn.coostack.cooparticlesapi.listeners.ClientPlayerDeathListener
import com.mojang.authlib.minecraft.client.MinecraftClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

@EventBusSubscriber
object ClientPlayerListener {
    @SubscribeEvent
    fun onPlayerDeath(event: ScreenEvent.Opening) {
        val screen = event.screen
        // 死亡屏幕出现时判定客户端死亡
        if (screen is DeathScreen) {
            ClientPlayerDeathListener.call()
        }
    }
}