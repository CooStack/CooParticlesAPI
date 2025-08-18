package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.listeners.ClientPlayerDeathListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class DeathScreenOpenListenerMinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    public void onSetScreen(Screen screen, CallbackInfo i) {
        if (screen instanceof DeathScreen) {
            ClientPlayerDeathListener.INSTANCE.call();
        }
    }
}
