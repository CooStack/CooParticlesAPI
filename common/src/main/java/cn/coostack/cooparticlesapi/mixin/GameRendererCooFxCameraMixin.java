package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.coofx.client.CooFxCameraTrackingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 使用当前服务端权威 CooFX scene 选中的透视 camera 垂直 FOV。 */
@Mixin(GameRenderer.class)
public class GameRendererCooFxCameraMixin {
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"
            ),
            index = 2
    )
    private boolean cooParticlesAPI$renderLocalPlayerForCooFxCamera(boolean detached) {
        return detached || CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxWalkingBob(CallbackInfo callback) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxHurtBob(CallbackInfo callback) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxFirstPersonHands(
            Camera camera,
            float partialTick,
            Matrix4f projectionMatrix,
            CallbackInfo callback
    ) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }

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
