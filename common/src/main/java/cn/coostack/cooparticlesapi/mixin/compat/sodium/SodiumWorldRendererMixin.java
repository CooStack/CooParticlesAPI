package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import cn.coostack.cooparticlesapi.compat.IrisCompat;
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 连接 Sodium 0.9.2 实际绘制入口，并按 OIT 阶段隔离覆盖颜色提交。 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {
    /** 负责当前帧排序与可见区段列表的原版 Sodium 管理器。 */
    @Shadow(remap = false)
    private RenderSectionManager renderSectionManager;

    /** RenderPass 已由调用方创建，开始透明层前保留此刻的活动场景深度。 */
    @Inject(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDD" +
                    "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;" +
                    "Lcom/mojang/renderpearl/api/textures/GpuSampler;" +
                    "Lcom/mojang/renderpearl/api/commands/RenderPass;" +
                    "Lnet/minecraft/client/renderer/oit/OitStage;)V",
            at = @At("HEAD")
    )
    private void cooParticlesAPI$captureDepthBefore(
            ChunkRenderMatrices matrices, TerrainRenderPass pass,
            double cameraX, double cameraY, double cameraZ,
            FogParameters fog, GpuSampler sampler, RenderPass renderPass, OitStage oitStage,
            CallbackInfo info
    ) {
        if (!IrisCompat.shouldSkipShadowPass() && pass.isTranslucent() &&
                (oitStage == null || oitStage == OitStage.ACCUMULATE)) {
            if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
                CooTerrainPipelineManager.captureTranslucentTerrainDepthBefore();
            } else {
                CooTerrainPipelineManager.captureTranslucentTerrainDepthBeforeFromCurrentFramebuffer();
            }
        }
    }

    /**
     * 普通层立即提交；原版 OIT 在三个阶段执行对应变体，共享原版透明累积。
     * 多输出等不支持适配的 shader 保留有日志提示的晚期原程序合成；Iris 保留原有延迟策略。
     */
    @Inject(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDD" +
                    "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;" +
                    "Lcom/mojang/renderpearl/api/textures/GpuSampler;" +
                    "Lcom/mojang/renderpearl/api/commands/RenderPass;" +
                    "Lnet/minecraft/client/renderer/oit/OitStage;)V",
            at = @At("TAIL")
    )
    private void cooParticlesAPI$renderTerrainOverlays(
            ChunkRenderMatrices matrices, TerrainRenderPass pass,
            double cameraX, double cameraY, double cameraZ,
            FogParameters fog, GpuSampler sampler, RenderPass renderPass, OitStage oitStage,
            CallbackInfo info
    ) {
        if (!IrisCompat.shouldSkipShadowPass() && (oitStage == null || oitStage == OitStage.ACCUMULATE)) {
            if (pass.isTranslucent()) {
                if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
                    CooTerrainPipelineManager.captureTranslucentTerrainDepthAfter();
                } else {
                    CooTerrainPipelineManager.captureTranslucentTerrainDepthAfterFromCurrentFramebuffer();
                }
            } else {
                if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
                    CooTerrainPipelineManager.captureOpaqueTerrainDepth();
                } else {
                    CooTerrainPipelineManager.captureOpaqueTerrainDepthFromCurrentFramebuffer();
                }
            }
        }
        CooSodiumTerrainOverlay.renderOrDefer(
                renderSectionManager.getRenderLists(), matrices, pass,
                cameraX, cameraY, cameraZ, oitStage != null, oitStage
        );
    }

    /** 资源重载时关闭覆盖层旧格式、排序缓冲及 shader 延迟引用。 */
    @Inject(method = "reload", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlaysForReload(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
