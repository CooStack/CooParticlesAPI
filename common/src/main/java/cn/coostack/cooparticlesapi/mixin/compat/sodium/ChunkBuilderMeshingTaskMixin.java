package cn.coostack.cooparticlesapi.mixin.compat.sodium;

import cn.coostack.cooparticlesapi.renderer.terrain.sodium.CooSodiumTerrainOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

/** 将 Sodium 编译与覆盖资源绑定为同一异常安全生命周期。 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
    /** 取消和异常都进入 finally，成功转交后重复清理不会触碰已归属结果的资源。 */
    @WrapMethod(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;" +
                    "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)" +
                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;"
    )
    private ChunkBuildOutput compileTerrainOverlayBuild(ChunkBuildContext context,
                                                       CancellationToken cancellationToken,
                                                       Operation<ChunkBuildOutput> original) {
        CooSodiumTerrainOverlay.beginBuild();
        try {
            ChunkBuildOutput result = original.call(context, cancellationToken);
            CooSodiumTerrainOverlay.finishBuild(result);
            return result;
        } finally {
            CooSodiumTerrainOverlay.finishBuild(null);
        }
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

}
