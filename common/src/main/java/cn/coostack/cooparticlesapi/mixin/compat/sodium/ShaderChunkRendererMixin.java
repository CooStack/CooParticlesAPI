package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.compat.IrisCompat;
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Iris 为 Sodium terrain 绑定的活动 framebuffer 生命周期内捕获地形深度。 */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public class ShaderChunkRendererMixin {
    @Inject(method = "begin", at = @At("RETURN"), remap = false)
    private void cooParticlesAPI$captureTerrainDepthBefore(TerrainRenderPass pass, CallbackInfo info) {
        if (skipShadowPass()) {
            return;
        }
        if (pass.isTranslucent()) {
            CooTerrainPipelineManager.captureTranslucentTerrainDepthBeforeFromCurrentFramebuffer();
        }
    }

    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void cooParticlesAPI$captureTerrainDepthAfter(TerrainRenderPass pass, CallbackInfo info) {
        if (skipShadowPass()) {
            return;
        }
        if (pass.isTranslucent()) {
            CooTerrainPipelineManager.captureTranslucentTerrainDepthAfterFromCurrentFramebuffer();
        } else {
            CooTerrainPipelineManager.captureOpaqueTerrainDepthFromCurrentFramebuffer();
        }
    }

    /** Iris 状态无法确认时采用保守策略，避免把 shadow framebuffer 当作主场景快照。 */
    private static boolean skipShadowPass() {
        return IrisCompat.shouldSkipShadowPass();
    }
}
