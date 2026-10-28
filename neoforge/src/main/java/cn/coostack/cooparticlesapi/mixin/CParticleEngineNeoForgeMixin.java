package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 26.3 主帧图回调完成原版绘制后提交 NeoForge GPU 粒子。 */
@Mixin(LevelRenderer.class)
public abstract class CParticleEngineNeoForgeMixin {
    /** 与 Fabric 共用实际提交能力，不依赖已移除的 ParticleEngine 过滤器重载。 */
    @Inject(
            method = "lambda$addMainPass$0(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;ZZ)V",
            at = @At("TAIL")
    )
    private void renderGpuParticles(CallbackInfo info) {
        CParticleSystemManager.renderSubmittedParticlePass();
    }
}
