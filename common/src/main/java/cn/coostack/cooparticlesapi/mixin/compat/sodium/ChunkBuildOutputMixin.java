package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkBuildOutput.class, remap = false)
public class ChunkBuildOutputMixin {
    @Inject(method = "destroy", at = @At("HEAD"))
    private void cooParticlesAPI$discardPendingTerrainOverlay(CallbackInfo info) {
        CooSodiumTerrainOverlay.discardBuildOutput((ChunkBuildOutput) (Object) this);
    }
}
