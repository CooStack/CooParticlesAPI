package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class BlockRendererMixin extends AbstractBlockRenderContext {
    @Shadow(remap = false)
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/" +
                            "TerrainRenderPass;isTranslucent()Z"
            ),
            cancellable = true
    )
    private void cooParticlesAPI$captureTerrainOverlay(MutableQuadViewImpl quad,
                                                        float[] brightness,
                                                        Material material,
                                                        CallbackInfo info,
                                                        @Local(name = "pass") TerrainRenderPass pass) {
        RenderType baseLayer = CooSodiumTerrainOverlay.resolveBaseLayer(type, material, pass);
        List<RenderType> overlays = CooTerrainPipelineManager.resolveOverlayRenderTypes(state, baseLayer, pos);
        if (overlays.isEmpty()) {
            return;
        }
        for (RenderType overlay : overlays) {
            CooSodiumTerrainOverlay.captureQuad(overlay, pass, pos, quad, vertices);
        }
        if (!CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry(overlays)) {
            info.cancel();
        }
    }
}
