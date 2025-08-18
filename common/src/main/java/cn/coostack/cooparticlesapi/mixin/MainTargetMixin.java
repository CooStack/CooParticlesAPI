package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public class MainTargetMixin {

    @Shadow
    public int frameBufferId;

    @Inject(method = "_resize", at = @At("TAIL"))
    public void resize(int width, int height, boolean clearError, CallbackInfo ci) {
        int mainID = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
        if (mainID != frameBufferId) {
            return;
        }
        ClientRenderPipelineManager.INSTANCE.resizeTo(width, height);
    }
}
