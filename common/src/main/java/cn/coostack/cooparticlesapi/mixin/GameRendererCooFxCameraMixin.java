package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.coofx.client.CooFxCameraTrackingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 使用当前服务端权威 CooFX scene 选中的透视 camera 垂直 FOV。 */
@Mixin(GameRenderer.class)
public class GameRendererCooFxCameraMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cooParticlesAPI$applyCooFxCameraFov(
            Camera camera,
            float partialTick,
            boolean useFovSetting,
            CallbackInfoReturnable<Double> callback
    ) {
        Double fov = CooFxCameraTrackingManager.activePerspectiveFovDegrees();
        if (fov != null) {
            callback.setReturnValue(fov);
        }
    }
}
