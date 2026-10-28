package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.CooParticlesAPIClient;
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将 Coo 世界特效接入 26.3 的提取状态、FrameGraph 执行与 OIT 合成生命周期。
 *
 * <p>只在已执行的绘制阶段访问场景目标，不在构建 FrameGraph 时执行 OpenGL 绘制。
 * Iris 最终合成在同一 popMatrix 入口的高优先级 hook 中完成，本类随后提交覆盖与后处理。
 */
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class LevelRendererMixin {
    /** 当前帧由原版提取并传入世界渲染器的状态，不缓存世界实体。 */
    @Shadow @Final private LevelRenderState levelRenderState;

    /** 清除上一帧未消费的粒子和地形状态，并处理本帧变更。 */
    @Inject(method = "render", at = @At("HEAD"))
    private void beginCooRenderFrame(CallbackInfo callback) {
        CParticleSystemManager.beginRenderFrame();
        CooTerrainPipelineManager.updateCompatibilityState();
        CooTerrainPipelineManager.beginRenderFrame(levelRenderState.worldPartialTicks);
        CooParticlesAPIClient.initShaderPrograms();
        CooParticlesAPIClient.syncRenderBackend();
    }

    /** 此时主 RenderPass 已打开，真实颜色和深度目标均已绑定。 */
    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void beginCooWorldPass(CallbackInfo callback) {
        CameraRenderState camera = levelRenderState.cameraRenderState;
        ClientRenderPipelineManager.INSTANCE.beginFrame(
                levelRenderState.worldPartialTicks, camera.viewRotationMatrix, camera.projectionMatrix);
        ClientRenderEntityManager.INSTANCE.beginWorldRenderFrame();
    }

    /** 地形已绘制而实体尚未执行时捕获不透明深度，并保留 Iris 实体世界通道。 */
    @Inject(method = "executeSolid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void beforeCooEntityFeatures(CallbackInfo callback) {
        CameraRenderState camera = levelRenderState.cameraRenderState;
        if (!CooTerrainPipelineManager.usesSodiumTerrainOverlay()) {
            VanillaTerrainOverlay.renderOpaque(
                    (LevelRenderer) (Object) this, camera, levelRenderState.worldPartialTicks);
        }
        CooTerrainPipelineManager.captureOpaqueTerrainDepth();
        ClientRenderEntityManager.INSTANCE.renderIrisWorldPass(
                levelRenderState.worldPartialTicks,
                camera.viewRotationMatrix,
                camera.projectionMatrix,
                CooParticlesAPIClient.checkIrisShaderPackUsed());
        ClientRenderPipelineManager.INSTANCE.renderIrisCooFxWorldPass();
    }

    /** 两条透明路线共用明确的阶段边界，不依赖旧 renderSectionLayer 的 ordinal。 */
    @Inject(method = {"executeClassicTransparency", "executeOit"}, at = @At("HEAD"))
    private void beforeCooTranslucents(CallbackInfo callback) {
        CooTerrainPipelineManager.captureTranslucentTerrainDepthBefore();
    }

    /** OIT 辅助颜色不作为 SceneColor，只捕获对应阶段的深度。 */
    @Inject(method = {"executeClassicTransparency", "executeOit"}, at = @At("RETURN"))
    private void afterCooTranslucents(CallbackInfo callback) {
        CooTerrainPipelineManager.captureTranslucentTerrainDepthAfter();
    }

    /**
     * 仅在原版正在执行的 terrain OIT 调用范围借用目标。
     * Sodium 在原调用内部提交覆盖；原版路径随后为覆盖创建同参数 pass。
     */
    @WrapOperation(method = "executeOit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderOit(" +
                    "Lcom/mojang/renderpearl/api/textures/GpuSampler;" +
                    "Lnet/minecraft/client/renderer/oit/OitStage;" +
                    "Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;" +
                    "Lcom/mojang/renderpearl/api/textures/GpuTextureView;" +
                    "Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void submitCooOitTerrain(
            ChunkSectionsToRender sections, GpuSampler sampler, OitStage stage,
            OitRenderPassProvider.Parameters parameters, GpuTextureView atlas, GpuTextureView lightmap,
            Operation<Void> original
    ) {
        if (CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
            original.call(sections, sampler, stage, parameters, atlas, lightmap);
            return;
        }
        CooTerrainPipelineManager.beginNativeOit(stage, parameters);
        try {
            original.call(sections, sampler, stage, parameters, atlas, lightmap);
            if (!CooTerrainPipelineManager.usesSodiumTerrainOverlay()) {
                VanillaTerrainOverlay.renderOit((LevelRenderer) (Object) this,
                        levelRenderState.cameraRenderState, stage, parameters);
            }
        } finally {
            CooTerrainPipelineManager.endNativeOit();
        }
    }

    /**
     * FrameGraph 和 Iris finalize 均完成后、手部清深度前执行世界覆盖。
     *
     * <p>矩阵仍由本帧 CameraRenderState 提供，不读取已经清空的 FrameGraph 资源句柄。
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void finishCooWorldFrame(CallbackInfo callback) {
        CameraRenderState camera = levelRenderState.cameraRenderState;
        if (!CooTerrainPipelineManager.usesSodiumTerrainOverlay()) {
            VanillaTerrainOverlay.render(
                    (LevelRenderer) (Object) this, camera, levelRenderState.worldPartialTicks);
        }
        if (!CooTerrainPipelineManager.deferFrameFinish(
                levelRenderState.worldPartialTicks, camera.viewRotationMatrix, camera.projectionMatrix)) {
            ClientRenderPipelineManager.INSTANCE.finishLevelRender(
                    levelRenderState.worldPartialTicks, camera.viewRotationMatrix, camera.projectionMatrix);
        }
    }
}
