package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class RenderSectionManagerMixin {
    @Shadow(remap = false)
    private SortedRenderLists renderLists;

    @Inject(method = "renderLayer", at = @At("HEAD"))
    private void cooParticlesAPI$captureTranslucentTerrainDepthBefore(ChunkRenderMatrices matrices,
                                                                       TerrainRenderPass pass,
                                                                       double cameraX,
                                                                       double cameraY,
                                                                       double cameraZ,
                                                                       CallbackInfo info) {
        if (pass.isTranslucent()) {
            CooTerrainPipelineManager.captureTranslucentTerrainDepthBefore();
        }
    }

    @Inject(method = "renderLayer", at = @At("TAIL"))
    private void cooParticlesAPI$renderTerrainOverlays(ChunkRenderMatrices matrices,
                                                        TerrainRenderPass pass,
                                                        double cameraX,
                                                        double cameraY,
                                                        double cameraZ,
                                                        CallbackInfo info) {
        if (pass.isTranslucent()) {
            CooTerrainPipelineManager.captureTranslucentTerrainDepthAfter();
        }
        CooSodiumTerrainOverlay.renderOrDefer(renderLists, matrices, pass, cameraX, cameraY, cameraZ);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlays(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
