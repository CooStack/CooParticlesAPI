package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 Sodium 0.9.2 完成四顶点颜色、AO、UV 与位置后收集地形覆盖。
 * 原版几何是否保留仍由统一兼容策略决定，Iris 或降级状态不得在这里强制取消。
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class BlockRendererMixin extends AbstractBlockRenderContext {
    /** Sodium 本次面写入前已经填好的区段局部四顶点。 */
    @Shadow(remap = false)
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(
            method = "bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;" +
                    "[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/" +
                            "TerrainRenderPass;isTranslucent()Z"
            ),
            cancellable = true
    )
    /** 保留材质选择的 pass 身份，避免依赖编译器局部变量名称。 */
    private void cooParticlesAPI$captureTerrainOverlay(MutableQuadViewImpl quad,
                                                        float[] brightness,
                                                        Material material,
                                                        CallbackInfo info) {
        TerrainRenderPass pass = material.pass;
        RenderType baseLayer = CooSodiumTerrainOverlay.resolveBaseLayer(quad.getRenderType(), material, pass);
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
