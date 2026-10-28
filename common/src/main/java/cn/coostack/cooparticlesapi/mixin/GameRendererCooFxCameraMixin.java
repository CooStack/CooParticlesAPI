package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.coofx.client.CooFxCameraTrackingManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CooFX 相机生效时抑制晃动和手部提交；姿态与 FOV 由相机提取前的专用 Mixin 处理。 */
@Mixin(GameRenderer.class)
public class GameRendererCooFxCameraMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxWalkingBob(
            CameraRenderState cameraState,
            PoseStack poseStack,
            CallbackInfo callback
    ) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxHurtBob(
            CameraRenderState cameraState,
            PoseStack poseStack,
            CallbackInfo callback
    ) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$suppressCooFxFirstPersonHands(
            CameraRenderState cameraState,
            PlayerRenderState playerState,
            GpuTextureView itemEntityTarget,
            CallbackInfo callback
    ) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            callback.cancel();
        }
    }
}
