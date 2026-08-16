package cn.coostack.cooparticlesapi.mixin.compat.iris;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Iris final pass 前捕获场景 attachment，并在完成后提交最终屏幕后处理。 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisRenderingPipelineMixin {
    @Inject(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"
            ),
            require = 0,
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
            require = 0,
            remap = false
    )
    private void cooparticlesapi$renderScenePostAfterFinalPass(CallbackInfo ci) {
        ClientRenderPipelineManager.INSTANCE.renderIrisScenePost();
    }
}
