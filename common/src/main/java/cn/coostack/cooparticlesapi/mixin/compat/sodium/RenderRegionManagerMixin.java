package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** 把 Sodium 0.9.2 区段上传与回收生命周期传递给独立地形覆盖缓存。 */
@Mixin(value = RenderRegionManager.class, remap = false)
public class RenderRegionManagerMixin {
    @Inject(
            method = "uploadResults(Ljava/util/Collection;" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
            at = @At("HEAD")
    )
    /** 在 Sodium 销毁 CPU 构建结果前复制并上传覆盖层数据。 */
    private void cooParticlesAPI$uploadTerrainOverlays(Collection<BuilderTaskOutput> outputs,
                                                        UniformBufferManager uniformBuffers,
                                                        CallbackInfo info) {
        CooSodiumTerrainOverlay.uploadResults(outputs);
    }

    @Inject(method = "delete", at = @At("HEAD"))
    /** 世界或渲染器回收时关闭本库拥有的覆盖层资源。 */
    private void cooParticlesAPI$releaseTerrainOverlays(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
