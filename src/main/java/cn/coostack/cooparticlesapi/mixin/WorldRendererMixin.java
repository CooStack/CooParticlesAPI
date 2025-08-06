package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
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
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Shadow
    private @Nullable ClientWorld world;

    @Inject(method = "render", at = @At("TAIL"))
    public void render(RenderTickCounter tickCounter,
                       boolean renderBlockOutline,
                       Camera camera,
                       GameRenderer gameRenderer,
                       LightmapTextureManager lightmapTextureManager,
                       Matrix4f matrix4f,
                       Matrix4f matrix4f2,
                       CallbackInfo ci
    ) {
        if (world == null) {
            return;
        }
        boolean shouldTick = world.getTickManager().shouldTick();
        float tickDelta = tickCounter.getTickDelta(!shouldTick);
        ClientRenderEntityManager.INSTANCE.renderTick(tickDelta, matrix4f, matrix4f2);
    }
}
