package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void cooParticlesAPI$renderTerrainAfterIris(DeltaTracker deltaTracker, CallbackInfo info) {
        try {
            CooSodiumTerrainOverlay.flushDeferred();
        } finally {
            CooTerrainPipelineManager.finishDeferredFrame();
        }
    }
}
