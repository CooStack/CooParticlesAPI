package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 把独立覆盖 CPU/GPU 生命周期绑定到原版 26.3 已编译区段对象。 */
@Mixin(CompiledSectionMesh.class)
public abstract class CompiledSectionMeshMixin {
    /** 构造成功后从 Results 接管覆盖几何，不修改原版层枚举映射。 */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void attachOverlayResult(TranslucencyPointOfView view, SectionCompiler.Results results, long startedAt,
                                     CallbackInfo callback) {
        VanillaTerrainOverlay.attach(results, (CompiledSectionMesh) (Object) this);
    }

    /** 原版几何被全部替换时仍把区段保留在可见渲染集合中。 */
    @Inject(method = "hasRenderableLayers", at = @At("HEAD"), cancellable = true)
    private void includeOverlayGeometry(CallbackInfoReturnable<Boolean> callback) {
        if (VanillaTerrainOverlay.hasGeometry((CompiledSectionMesh) (Object) this)) {
            callback.setReturnValue(true);
        }
    }

    /** 允许原版任何关闭路径回收覆盖；资源账本保证幂等。 */
    @Inject(method = "close", at = @At("HEAD"))
    private void releaseOverlayGeometry(CallbackInfo callback) {
        VanillaTerrainOverlay.release((CompiledSectionMesh) (Object) this);
    }
}
