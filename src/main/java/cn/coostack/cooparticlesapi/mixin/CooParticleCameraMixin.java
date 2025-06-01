package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.CameraUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
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
//        float newYaw = yaw + CameraUtil.INSTANCE.getCurrentYawOffset();
//        float newPitch = pitch + CameraUtil.INSTANCE.getCurrentPitchOffset();
//        setRotation(newYaw, newPitch);
        double x = pos.getX() + CameraUtil.INSTANCE.getCurrentXOffset();
        double y = pos.getY() + CameraUtil.INSTANCE.getCurrentYOffset();
        double z = pos.getZ() + CameraUtil.INSTANCE.getCurrentZOffset();
        setPos(x, y, z);
    }
}
