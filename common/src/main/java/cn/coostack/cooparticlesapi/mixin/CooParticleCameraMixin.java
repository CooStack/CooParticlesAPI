package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.ClientCameraUtil;
import cn.coostack.cooparticlesapi.utils.GraphMathHelper;
import cn.coostack.cooparticlesapi.coofx.client.CooFxCameraTrackingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原版相机完成实体定位后应用库内偏移，并让 CooFX 相机参与同一帧的投影和裁剪。 */
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

    /** 提取阶段据此决定是否绘制被跟踪的本地玩家，不修改用户的视角选项。 */
    @Shadow
    private boolean detached;

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

    /** 在投影与视锥更新之前应用位置和方向，避免提取旧姿态后再修改相机。 */
    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void onUpdate(float partialTick, CallbackInfo ci) {
        float lerpYawOffset = GraphMathHelper.lerp(partialTick, lastYawOffset, targetYawOffset);
        float lerpPitchOffset = GraphMathHelper.lerp(partialTick, lastPitchOffset, targetPitchOffset);
        Vec3 lerpPosOffset = GraphMathHelper.lerp(partialTick, lastPosOffset, targetPosOffset);
        float forceBlend = GraphMathHelper.lerp(partialTick, lastForcedBlend, targetForcedBlend);
        Vec3 forcedPos = GraphMathHelper.lerp(partialTick, lastForcedPos, targetForcedPos);

        setRotation(lerpYawOffset + yRot, lerpPitchOffset + xRot);
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            detached = true;
        }

        Vec3 freePos = position.add(lerpPosOffset);
        if (forceBlend > 0.0001F) {
            Vec3 forcedPosWithOffset = forcedPos.add(lerpPosOffset);
            setPosition(GraphMathHelper.lerp(forceBlend, freePos, forcedPosWithOffset));
            return;
        }
        setPosition(freePos);
    }

    /** 获胜透视相机的垂直 FOV 直接进入原版投影和裁剪，不覆盖 HUD 或玩家视角设置。 */
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void applyCooFxFieldOfView(float partialTick, CallbackInfoReturnable<Float> callback) {
        Double fov = CooFxCameraTrackingManager.activePerspectiveFovDegrees();
        if (fov != null) {
            callback.setReturnValue(fov.floatValue());
        }
    }

    /** 仅在外部相机生效时公开第三人称渲染状态，放弃 claim 后由原版重新提取。 */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void extractCooFxCameraState(CameraRenderState state, DeltaTracker deltaTracker, CallbackInfo callback) {
        if (CooFxCameraTrackingManager.isVanillaCameraEffectsSuppressed()) {
            state.isFirstPerson = false;
        }
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

