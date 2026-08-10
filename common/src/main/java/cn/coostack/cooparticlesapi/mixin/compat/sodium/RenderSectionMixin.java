package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSection.class, remap = false)
public class RenderSectionMixin {
    @Inject(method = "delete", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlay(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseSection((RenderSection) (Object) this);
    }
}
