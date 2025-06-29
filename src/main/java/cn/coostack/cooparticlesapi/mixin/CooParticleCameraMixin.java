package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.ClientCameraUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CooParticleCameraMixin {
    @Shadow
    private float yaw;

    @Shadow
    private float pitch;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    private Vec3d pos;

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(CallbackInfo ci) {
        float newYaw = yaw + ClientCameraUtil.INSTANCE.getShakeYawOffset$coo_particles_api() + ClientCameraUtil.INSTANCE.getCurrentYawOffset();
        float newPitch = pitch + ClientCameraUtil.INSTANCE.getShakePitchOffset$coo_particles_api() + ClientCameraUtil.INSTANCE.getCurrentPitchOffset();
        double x = pos.getX() + ClientCameraUtil.INSTANCE.getShakeXOffset$coo_particles_api() + ClientCameraUtil.INSTANCE.getCurrentXOffset();
        double y = pos.getY() + ClientCameraUtil.INSTANCE.getShakeYOffset$coo_particles_api() + ClientCameraUtil.INSTANCE.getCurrentYOffset();
        double z = pos.getZ() + ClientCameraUtil.INSTANCE.getShakeZOffset$coo_particles_api() + ClientCameraUtil.INSTANCE.getCurrentZOffset();
        setRotation(newYaw, newPitch);
        setPos(x, y, z);
    }
}
