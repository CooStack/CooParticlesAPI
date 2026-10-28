package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 缓冲池取消、异常丢弃或关闭时回收仍绑定该 pack 的覆盖构建器。 */
@Mixin(SectionBufferBuilderPack.class)
public abstract class SectionBufferBuilderPackMixin {
    /** 已转交 Results 的独立缓冲不受 pack 清空影响；这里只回收未完成的构建。 */
    @Inject(method = {"clearAll", "discardAll", "close"}, at = @At("HEAD"))
    private void cancelOverlayBuild(CallbackInfo callback) {
        VanillaTerrainOverlay.cancelBuild((SectionBufferBuilderPack) (Object) this);
    }
}
