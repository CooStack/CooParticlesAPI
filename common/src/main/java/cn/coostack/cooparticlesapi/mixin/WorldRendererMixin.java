package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 用于添加到渲染的最末尾 (避免光影影响)
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    @Shadow
    private @Nullable ClientLevel level;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    public void render(DeltaTracker tickCounter,
                       boolean renderBlockOutline,
                       Camera camera,
                       GameRenderer gameRenderer,
                       LightTexture lightmapTextureManager,
                       Matrix4f viewMatrix,
                       Matrix4f projectionMatrix,
                       CallbackInfo ci
    ) {
        if (level == null) {
            return;
        }
        boolean shouldTick = level.tickRateManager().runsNormally();
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(!shouldTick);
        ClientRenderEntityManager.INSTANCE.renderTick(tickDelta, viewMatrix, projectionMatrix);
    }
}
