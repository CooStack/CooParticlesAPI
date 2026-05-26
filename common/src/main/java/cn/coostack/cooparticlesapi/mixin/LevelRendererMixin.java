package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.CooParticlesAPIClient;
import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

        if (level == null) {
            return;
        }
        CooParticlesAPIClient.initShaderPrograms();
        CooParticlesAPIClient.syncRenderBackend();
        boolean shouldTick = level.tickRateManager().runsNormally();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(!shouldTick);
        ClientRenderPipelineManager.INSTANCE.beginFrame(tickDelta, frustumMatrix, projectionMatrix);
        MultiBufferSource.BufferSource bufferSource =
                ((LevelRendererAccessor) this).renderBuffers().bufferSource();
        ClientRenderEntityManager.INSTANCE.renderRenderTypePass(
                tickDelta,
                frustumMatrix,
                projectionMatrix,
                new PoseStack(),
                bufferSource,
                camera,
                CooParticlesAPIClient.checkIrisShaderPackUsed()
        );
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

        if (level == null) {
            return;
        }
        CooParticlesAPIClient.initShaderPrograms();
        CooParticlesAPIClient.syncRenderBackend();
        boolean shouldTick = level.tickRateManager().runsNormally();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(!shouldTick);
        ClientRenderPipelineManager.INSTANCE.finishLevelRender(tickDelta, frustumMatrix, projectionMatrix);
    }
}
