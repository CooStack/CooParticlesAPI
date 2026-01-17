package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.ClientCameraUtil;
import cn.coostack.cooparticlesapi.utils.GraphMathHelper;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author CooStack
 */
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

    @Shadow
    protected abstract void setPosition(Vec3 vec3);

    @Unique
    private float targetYawOffset = 0f;
    @Unique
    private float targetPitchOffset = 0f;
    @Unique
    private Vec3 targetPosOffset = Vec3.ZERO;

    @Unique
    private float lastYawOffset = 0f;
    @Unique
    private float lastPitchOffset = 0f;
    @Unique
    private Vec3 lastPosOffset = Vec3.ZERO;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onUpdate(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        // 插值
        var lerpYawOffset = GraphMathHelper.lerp(partialTick, lastYawOffset, targetYawOffset);
        var lerpPitchOffset = GraphMathHelper.lerp(partialTick, lastPitchOffset, targetPitchOffset);
        var lerpPosOffset = GraphMathHelper.lerp(partialTick, lastPosOffset, targetPosOffset);
        setRotation(lerpYawOffset + yRot, lerpPitchOffset + xRot);
        setPosition(lerpPosOffset.add(position));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        float newYaw = ClientCameraUtil.INSTANCE.getShakeYawOffset() + ClientCameraUtil.INSTANCE.getCurrentYawOffset();
        float newPitch = ClientCameraUtil.INSTANCE.getShakePitchOffset() + ClientCameraUtil.INSTANCE.getCurrentPitchOffset();
        double x = ClientCameraUtil.INSTANCE.getShakeXOffset() + ClientCameraUtil.INSTANCE.getCurrentXOffset();
        double y = ClientCameraUtil.INSTANCE.getShakeYOffset() + ClientCameraUtil.INSTANCE.getCurrentYOffset();
        double z = ClientCameraUtil.INSTANCE.getShakeZOffset() + ClientCameraUtil.INSTANCE.getCurrentZOffset();
        targetYawOffset = newYaw;
        targetPitchOffset = newPitch;
        targetPosOffset = new Vec3(x, y, z);
        lastPosOffset = targetPosOffset;
        lastYawOffset = targetYawOffset;
        lastPitchOffset = targetPitchOffset;
    }
}
