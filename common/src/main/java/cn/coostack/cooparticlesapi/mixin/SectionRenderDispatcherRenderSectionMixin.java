package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 跟随原版 26.3 区段网格替换与取消路径回收覆盖资源，不再创建旧 VertexBuffer。 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionRenderDispatcherRenderSectionMixin {
    /** 原版 copyLock 内释放旧网格时同步移除覆盖账本；异步 GPU 删除由资源所有者安排。 */
    @Inject(method = "releaseSectionMesh", at = @At("HEAD"))
    private void releaseOverlayMesh(SectionMesh mesh, CallbackInfo callback) {
        VanillaTerrainOverlay.release(mesh);
    }
}
