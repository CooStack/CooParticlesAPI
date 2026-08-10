package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(value = RenderRegionManager.class, remap = false)
public class RenderRegionManagerMixin {
    @Inject(
            method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;" +
                    "Ljava/util/Collection;)V",
            at = @At("HEAD")
    )
    private void cooParticlesAPI$uploadTerrainOverlays(CommandList commandList,
                                                        Collection<BuilderTaskOutput> outputs,
                                                        CallbackInfo info) {
        CooSodiumTerrainOverlay.uploadResults(outputs);
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlays(CommandList commandList, CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
