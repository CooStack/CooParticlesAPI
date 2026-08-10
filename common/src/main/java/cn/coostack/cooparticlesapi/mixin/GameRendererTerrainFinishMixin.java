package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在没有 Sodium 时，于 Iris 最终合成后提交延迟的 terrain 覆盖层。 */
@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererTerrainFinishMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void cooParticlesAPI$finishTerrainAfterIris(DeltaTracker deltaTracker, CallbackInfo info) {
        if (CooTerrainPipelineManager.usesSodiumTerrainOverlay()) {
            return;
        }
        try {
            CooTerrainPipelineManager.flushDeferredVanillaTerrainOverlays();
        } finally {
            CooTerrainPipelineManager.finishDeferredFrame();
        }
    }
}
