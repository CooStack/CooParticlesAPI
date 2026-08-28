package cn.coostack.cooparticlesapi.mixin.compat.iris;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Iris composite 前捕获场景资源，并在 final 输出生成后执行场景后处理。 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisRenderingPipelineMixin {
    /** 在 Iris composite 修改颜色 attachment 前捕获场景资源。 */
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
