package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 区段管理器销毁时回收本库覆盖缓存；绘制已迁移到 SodiumWorldRenderer.renderLayer。 */
@Mixin(value = RenderSectionManager.class, remap = false)
public class RenderSectionManagerMixin {
    /** 关闭本库拥有的 CPU、GPU 与本帧延迟项。 */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlays(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
