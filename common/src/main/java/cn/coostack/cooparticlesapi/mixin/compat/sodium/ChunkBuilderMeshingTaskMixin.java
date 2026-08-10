package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;" +
                    "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD")
    )
    private void cooParticlesAPI$beginTerrainOverlayBuild(ChunkBuildContext context,
                                                           CancellationToken cancellationToken,
                                                           CallbackInfoReturnable<ChunkBuildOutput> callback) {
        CooSodiumTerrainOverlay.beginBuild();
    }

    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;" +
                    "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo$Builder;" +
                            "build()Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;"
            )
    )
    private void cooParticlesAPI$markTerrainOverlayPasses(ChunkBuildContext context,
                                                           CancellationToken cancellationToken,
                                                           CallbackInfoReturnable<ChunkBuildOutput> callback,
                                                           @Local(name = "renderData") BuiltSectionInfo.Builder renderData) {
        CooSodiumTerrainOverlay.markRenderPasses(renderData);
    }

    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;" +
                    "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("RETURN")
    )
    private void cooParticlesAPI$finishTerrainOverlayBuild(ChunkBuildContext context,
                                                            CancellationToken cancellationToken,
                                                            CallbackInfoReturnable<ChunkBuildOutput> callback) {
        CooSodiumTerrainOverlay.finishBuild(callback.getReturnValue());
    }
}
