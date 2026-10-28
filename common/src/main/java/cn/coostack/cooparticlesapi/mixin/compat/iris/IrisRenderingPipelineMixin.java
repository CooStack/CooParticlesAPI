package cn.coostack.cooparticlesapi.mixin.compat.iris;

import cn.coostack.cooparticlesapi.compat.iris.CooIrisRenderState;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.mixinterface.GpuTextureInterface;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 通过 Iris 生命周期桥接真实深度和 final 输出，避免在公共渲染逻辑中反射 Iris 私有状态。 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisRenderingPipelineMixin {
    @Shadow
    @Final
    private RenderTargets renderTargets;

    @Shadow
    @Final
    private FinalPassRenderer finalPassRenderer;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false)
    private void cooparticlesapi$beginFrame(CallbackInfo ci) {
        CooIrisRenderState.beginFrame();
    }

    @Inject(method = "beginLevelRendering", at = @At("TAIL"), remap = false)
    private void cooparticlesapi$captureSceneDepth(CallbackInfo ci) {
        CooIrisRenderState.captureDepthProjection(
                CapturedRenderingState.INSTANCE.getGbufferModelView(),
                CapturedRenderingState.INSTANCE.getGbufferProjection()
        );
        CooIrisRenderState.captureSceneDepth(
                ((GpuTextureInterface) renderTargets.getDepthTexture()).iris$getGlId(),
                renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight()
        );
    }

    @Inject(method = "beginTranslucents", at = @At("TAIL"), remap = false)
    private void cooparticlesapi$captureTerrainDepth(CallbackInfo ci) {
        GpuTexture depth = renderTargets.getDepthTextureNoTranslucents();
        CooIrisRenderState.captureTerrainDepth(
                ((GpuTextureInterface) depth).iris$getGlId(),
                renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight()
        );
    }

    @Inject(method = "beginHand", at = @At("TAIL"), remap = false)
    private void cooparticlesapi$captureNoHandDepth(CallbackInfo ci) {
        GpuTexture depth = renderTargets.getDepthTextureNoHand();
        CooIrisRenderState.captureNoHandDepth(
                ((GpuTextureInterface) depth).iris$getGlId(),
                renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight()
        );
    }

    @Inject(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"
            ),
            remap = false
    )
    private void cooparticlesapi$captureScenePostBeforeFinalPass(CallbackInfo ci) {
        ClientRenderPipelineManager.INSTANCE.captureIrisScenePost();
    }

    @Inject(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/FinalPassRenderer;renderFinalPass()V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void cooparticlesapi$captureFinalColorAndRenderPost(CallbackInfo ci) {
        GlFramebuffer colorHolder = ((FinalPassRendererAccessor) finalPassRenderer).getColorHolder();
        RenderTarget output = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        CooIrisRenderState.captureFinalColor(
                colorHolder.getColorAttachment(0),
                colorHolder.getId(),
                output.width,
                output.height
        );
        ClientRenderPipelineManager.INSTANCE.renderIrisScenePost();
    }

    @Inject(method = "destroy", at = @At("TAIL"), remap = false)
    private void cooparticlesapi$clearRenderState(CallbackInfo ci) {
        CooIrisRenderState.clear();
    }
}
