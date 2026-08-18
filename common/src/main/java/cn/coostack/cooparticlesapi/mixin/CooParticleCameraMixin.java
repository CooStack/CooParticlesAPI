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
    private float targetYawOffset = 0F;
    @Unique
    private float targetPitchOffset = 0F;
    @Unique
    private Vec3 targetPosOffset = Vec3.ZERO;

    @Unique
    private float lastYawOffset = 0F;
    @Unique
    private float lastPitchOffset = 0F;
    @Unique
    private Vec3 lastPosOffset = Vec3.ZERO;

    @Unique
    private Vec3 targetForcedPos = Vec3.ZERO;
    @Unique
    private Vec3 lastForcedPos = Vec3.ZERO;
    @Unique
    private float targetForcedBlend = 0F;
    @Unique
    private float lastForcedBlend = 0F;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onUpdate(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        float lerpYawOffset = GraphMathHelper.lerp(partialTick, lastYawOffset, targetYawOffset);
        float lerpPitchOffset = GraphMathHelper.lerp(partialTick, lastPitchOffset, targetPitchOffset);
        Vec3 lerpPosOffset = GraphMathHelper.lerp(partialTick, lastPosOffset, targetPosOffset);
        float forceBlend = GraphMathHelper.lerp(partialTick, lastForcedBlend, targetForcedBlend);
        Vec3 forcedPos = GraphMathHelper.lerp(partialTick, lastForcedPos, targetForcedPos);

        setRotation(lerpYawOffset + yRot, lerpPitchOffset + xRot);

        Vec3 freePos = position.add(lerpPosOffset);
        if (forceBlend > 0.0001F) {
            Vec3 forcedPosWithOffset = forcedPos.add(lerpPosOffset);
            setPosition(GraphMathHelper.lerp(forceBlend, freePos, forcedPosWithOffset));
            return;
        }
        setPosition(freePos);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        lastYawOffset = targetYawOffset;
        lastPitchOffset = targetPitchOffset;
        lastPosOffset = targetPosOffset;
        lastForcedPos = targetForcedPos;
        lastForcedBlend = targetForcedBlend;

        targetYawOffset = ClientCameraUtil.INSTANCE.getTotalYawOffset();
        targetPitchOffset = ClientCameraUtil.INSTANCE.getTotalPitchOffset();
        targetPosOffset = ClientCameraUtil.INSTANCE.getTotalPositionOffset();
        targetForcedPos = ClientCameraUtil.INSTANCE.getForcedCameraPosition();
        targetForcedBlend = ClientCameraUtil.INSTANCE.getForcedCameraBlend();
    }
}

