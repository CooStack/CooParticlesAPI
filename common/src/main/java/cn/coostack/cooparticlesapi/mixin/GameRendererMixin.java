package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void renderAfterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientRenderEntityManager.INSTANCE.flushPostProcess();
    }
}
