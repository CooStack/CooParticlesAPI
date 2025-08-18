package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.ClientCameraUtil;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CooParticleCameraMixin {
    @Shadow
    private float yRot;
    @Shadow
    private float xRot;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    private Vec3 position;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onUpdate(CallbackInfo ci) {
        float newYaw = yRot + ClientCameraUtil.INSTANCE.getShakeYawOffset() + ClientCameraUtil.INSTANCE.getCurrentYawOffset();
        float newPitch = xRot + ClientCameraUtil.INSTANCE.getShakePitchOffset() + ClientCameraUtil.INSTANCE.getCurrentPitchOffset();
        double x = position.x() + ClientCameraUtil.INSTANCE.getShakeXOffset() + ClientCameraUtil.INSTANCE.getCurrentXOffset();
        double y = position.y() + ClientCameraUtil.INSTANCE.getShakeYOffset() + ClientCameraUtil.INSTANCE.getCurrentYOffset();
        double z = position.z() + ClientCameraUtil.INSTANCE.getShakeZOffset() + ClientCameraUtil.INSTANCE.getCurrentZOffset();
        setRotation(newYaw, newPitch);
        setPosition(x, y, z);
    }
}
