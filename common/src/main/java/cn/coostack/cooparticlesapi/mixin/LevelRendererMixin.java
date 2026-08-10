package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.CooParticlesAPIClient;
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
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

    @Shadow
    private void renderSectionLayer(RenderType renderType,
                                    double cameraX,
                                    double cameraY,
                                    double cameraZ,
                                    Matrix4f frustumMatrix,
                                    Matrix4f projectionMatrix) {
        throw new AssertionError();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void cooParticlesAPI$beginRenderFrame(DeltaTracker deltaTracker,
                                                  boolean renderBlockOutline,
                                                  Camera camera,
                                                  GameRenderer gameRenderer,
                                                  LightTexture lightTexture,
                                                  Matrix4f frustumMatrix,
                                                  Matrix4f projectionMatrix,
                                                  CallbackInfo info) {
        CParticleSystemManager.beginRenderFrame();
        CooTerrainPipelineManager.updateCompatibilityState();
        CooTerrainPipelineManager.beginRenderFrame(deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderSolidTerrainPipelines(DeltaTracker deltaTracker,
                                                              boolean renderBlockOutline,
                                                              Camera camera,
                                                              GameRenderer gameRenderer,
                                                              LightTexture lightTexture,
                                                              Matrix4f frustumMatrix,
                                                              Matrix4f projectionMatrix,
                                                              CallbackInfo info) {
        renderTerrainPipelines(RenderType.solid(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderCutoutMippedTerrainPipelines(DeltaTracker deltaTracker,
                                                                    boolean renderBlockOutline,
                                                                    Camera camera,
                                                                    GameRenderer gameRenderer,
                                                                    LightTexture lightTexture,
                                                                    Matrix4f frustumMatrix,
                                                                    Matrix4f projectionMatrix,
                                                                    CallbackInfo info) {
        renderTerrainPipelines(RenderType.cutoutMipped(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderCutoutTerrainPipelines(DeltaTracker deltaTracker,
                                                               boolean renderBlockOutline,
                                                               Camera camera,
                                                               GameRenderer gameRenderer,
                                                               LightTexture lightTexture,
                                                               Matrix4f frustumMatrix,
                                                               Matrix4f projectionMatrix,
                                                               CallbackInfo info) {
        renderTerrainPipelines(RenderType.cutout(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderTranslucentTerrainPipelines(DeltaTracker deltaTracker,
                                                                   boolean renderBlockOutline,
                                                                   Camera camera,
                                                                   GameRenderer gameRenderer,
                                                                   LightTexture lightTexture,
                                                                   Matrix4f frustumMatrix,
                                                                   Matrix4f projectionMatrix,
                                                                   CallbackInfo info) {
        renderTerrainPipelines(RenderType.translucent(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 4,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderTripwireTerrainPipelines(DeltaTracker deltaTracker,
                                                                boolean renderBlockOutline,
                                                                Camera camera,
                                                                GameRenderer gameRenderer,
                                                                LightTexture lightTexture,
                                                                Matrix4f frustumMatrix,
                                                                Matrix4f projectionMatrix,
                                                                CallbackInfo info) {
        renderTerrainPipelines(RenderType.tripwire(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 5,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderFabulousTranslucentTerrainPipelines(DeltaTracker deltaTracker,
                                                                           boolean renderBlockOutline,
                                                                           Camera camera,
                                                                           GameRenderer gameRenderer,
                                                                           LightTexture lightTexture,
                                                                           Matrix4f frustumMatrix,
                                                                           Matrix4f projectionMatrix,
                                                                           CallbackInfo info) {
        renderTerrainPipelines(RenderType.translucent(), camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    ordinal = 6,
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderFabulousTripwireTerrainPipelines(DeltaTracker deltaTracker,
                                                                        boolean renderBlockOutline,
                                                                        Camera camera,
                                                                        GameRenderer gameRenderer,
                                                                        LightTexture lightTexture,
                                                                        Matrix4f frustumMatrix,
                                                                        Matrix4f projectionMatrix,
                                                                        CallbackInfo info) {
        renderTerrainPipelines(RenderType.tripwire(), camera, frustumMatrix, projectionMatrix);
    }

    private void renderTerrainPipelines(RenderType baseLayer,
                                        Camera camera,
                                        Matrix4f frustumMatrix,
                                        Matrix4f projectionMatrix) {
        if (!CooTerrainPipelineManager.isTerrainOverlayEnabled()) {
            return;
        }
        if (CooTerrainPipelineManager.usesSodiumTerrainOverlay()) {
            return;
        }
        var layers = CooTerrainPipelineManager.layersFor(baseLayer);
        if (layers.isEmpty()) {
            return;
        }
        CooTerrainPipelineManager.beginOverlayBatch(layers);
        try {
            if (!CooTerrainPipelineManager.isTerrainOverlayEnabled()) {
                return;
            }
            for (RenderType renderType : layers) {
                try {
                    renderSectionLayer(
                            renderType,
                            camera.getPosition().x,
                            camera.getPosition().y,
                            camera.getPosition().z,
                            frustumMatrix,
                            projectionMatrix
                    );
                    CooTerrainPipelineManager.recordPostDraw(renderType, () -> renderSectionLayer(
                            renderType,
                            camera.getPosition().x,
                            camera.getPosition().y,
                            camera.getPosition().z,
                            frustumMatrix,
                            projectionMatrix
                    ));
                } catch (RuntimeException error) {
                    if (!CooTerrainPipelineManager.handleOverlayDrawFailure(renderType, error)) {
                        throw error;
                    }
                    break;
                }
            }
        } finally {
            CooTerrainPipelineManager.endOverlayBatch();
        }
    }

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
        boolean irisShaderPackInUse = CooParticlesAPIClient.checkIrisShaderPackUsed();
        ClientRenderEntityManager.INSTANCE.beginWorldRenderFrame();
        ClientRenderEntityManager.INSTANCE.renderIrisWorldPass(
                tickDelta,
                frustumMatrix,
                projectionMatrix,
                irisShaderPackInUse
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
        if (CooTerrainPipelineManager.deferFrameFinish(tickDelta, frustumMatrix, projectionMatrix)) {
            return;
        }
        ClientRenderPipelineManager.INSTANCE.finishLevelRender(tickDelta, frustumMatrix, projectionMatrix);
    }
}
