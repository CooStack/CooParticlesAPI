package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void renderAfterWorld(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        ClientRenderPipelineManager.INSTANCE.endFrame();
    }
}
