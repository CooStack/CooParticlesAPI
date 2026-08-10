package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void cooParticlesAPI$releaseTerrainOverlaysForReload(CallbackInfo info) {
        CooSodiumTerrainOverlay.releaseAll();
    }
}
