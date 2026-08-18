package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.coofx.client.CooFxCameraTrackingManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 CooFX camera claim 生效时冻结鼠标转向输入。 */
@Mixin(MouseHandler.class)
public class CooFxCameraMouseMixin {
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void cooparticlesapi$suppressCooFxCameraMouse(
            long window,
            double xOffset,
            double yOffset,
            CallbackInfo callbackInfo
    ) {
        if (CooFxCameraTrackingManager.isMouseSuppressed()) {
            callbackInfo.cancel();
        }
    }
}
