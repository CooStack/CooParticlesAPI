package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在场景与手部深度边界处理原版 OIT，并保留 Iris 最终合成后的原有提交点。 */
@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererMixin {
    /** 提升世界帧标记并丢弃异常退出帧遗留的延迟几何。 */
    @Inject(method = "renderLevel()V", at = @At("HEAD"))
    private void cooParticlesAPI$beginTerrainFrame(CallbackInfo info) {
        CooSodiumTerrainOverlay.beginFrame();
    }

    /** FrameGraph 已完成 OIT resolve，但 render3dHud 尚未清除场景深度。 */
    @Inject(
            method = "renderLevel()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(" +
                            "Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Z" +
                            "Lnet/minecraft/client/renderer/state/level/CameraRenderState;" +
                            "Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cooParticlesAPI$renderTerrainAfterOit(CallbackInfo info) {
        CooSodiumTerrainOverlay.flushDeferred(false);
    }

    /** 保持 Iris 路径原有时序，并保证地形帧结束处理不因绘制失败被跳过。 */
    @Inject(method = "renderLevel()V", at = @At("RETURN"))
    private void cooParticlesAPI$renderTerrainAfterIris(CallbackInfo info) {
        try {
            CooSodiumTerrainOverlay.flushDeferred();
        } finally {
            CooTerrainPipelineManager.finishDeferredFrame();
        }
    }
}
