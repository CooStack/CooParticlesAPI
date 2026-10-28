package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager;
import cn.coostack.cooparticlesapi.renderer.backend.OpenGlRenderTargetBridge;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将 26.3 渲染目标的纹理生命周期同步给 OpenGL 桥接与主目标尺寸管理器。
 */
@Mixin(RenderTarget.class)
public class MainTargetMixin {

    /** attachment 销毁前释放非拥有型桥接 FBO，不接管原版纹理资源。 */
    @Inject(method = "destroyBuffers()V", at = @At("HEAD"))
    private void releaseBridge(CallbackInfo ci) {
        OpenGlRenderTargetBridge.release((RenderTarget) (Object) this);
    }

    /** 以目标对象身份识别主目标，不依赖不再存在的固定 FBO 字段。 */
    @Inject(method = "resize(II)V", at = @At("TAIL"))
    public void resize(int width, int height, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer gameRenderer = minecraft == null ? null : minecraft.gameRenderer;
        // GameRenderer 构造内部目标时，Minecraft 尚未发布该渲染器实例。
        if (gameRenderer == null || gameRenderer.mainRenderTarget() != (Object) this) {
            return;
        }
        ClientRenderPipelineManager.INSTANCE.resizeTo(width, height);
    }
}
