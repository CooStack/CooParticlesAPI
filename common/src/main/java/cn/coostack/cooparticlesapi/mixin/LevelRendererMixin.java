package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.CooParticlesAPI;
import cn.coostack.cooparticlesapi.CooParticlesAPIClient;
import cn.coostack.cooparticlesapi.platform.CooParticlesServices;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    ordinal = 11))
    public void renderBeforeEntity(DeltaTracker deltaTracker,
                                   boolean renderBlockOutline,
                                   Camera camera,
                                   GameRenderer gameRenderer,
                                   LightTexture lightTexture,
                                   Matrix4f frustumMatrix,
                                   Matrix4f projectionMatrix,
                                   CallbackInfo info) {

        if (CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            return;
        }
        if (level == null) return;
        CooParticlesAPIClient.initShaderPrograms();
        boolean shouldTick = level.tickRateManager().runsNormally();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(!shouldTick);
        ClientRenderEntityManager.INSTANCE.renderTick(tickDelta, frustumMatrix, projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    public void renderOnTail(DeltaTracker deltaTracker,
                             boolean renderBlockOutline,
                             Camera camera,
                             GameRenderer gameRenderer,
                             LightTexture lightTexture,
                             Matrix4f frustumMatrix,
                             Matrix4f projectionMatrix,
                             CallbackInfo info) {

        boolean irisLoaded = CooParticlesAPIClient.irisLoaded;
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            return;
        }
        if (!irisLoaded) {
            return;
        }
        if (level == null) return;
        CooParticlesAPIClient.initShaderPrograms();
        boolean shouldTick = level.tickRateManager().runsNormally();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(!shouldTick);
        ClientRenderEntityManager.INSTANCE.renderTick(tickDelta, frustumMatrix, projectionMatrix);
    }


}
