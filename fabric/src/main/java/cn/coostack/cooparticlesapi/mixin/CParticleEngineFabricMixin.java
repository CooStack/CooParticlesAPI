package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 26.3 主帧图回调完成原版绘制后提交 Fabric GPU 粒子。 */
@Mixin(LevelRenderer.class)
public abstract class CParticleEngineFabricMixin {
    /** 原版 RenderPass 均已关闭，允许受保护的 OpenGL 提交。 */
    @Inject(
            method = "lambda$addMainPass$0(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;ZZ)V",
            at = @At("TAIL")
    )
    private void renderGpuParticles(CallbackInfo info) {
        CParticleSystemManager.renderSubmittedParticlePass();
    }
}
