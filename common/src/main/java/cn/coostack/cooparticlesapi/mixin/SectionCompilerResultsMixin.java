package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 取消编译结果时回收尚未转交已编译区段的覆盖 CPU 数据。 */
@Mixin(SectionCompiler.Results.class)
public abstract class SectionCompilerResultsMixin {
    /** 成功转移后的 Results 已无覆盖资源，重复释放不会影响活动区段。 */
    @Inject(method = "release", at = @At("HEAD"))
    private void releaseOverlayResult(CallbackInfo callback) {
        VanillaTerrainOverlay.releaseResult((SectionCompiler.Results) (Object) this);
    }
}
