package cn.coostack.cooparticlesapi.mixin.events.world.client;

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashMap;
import java.util.Map;

/** 只在已获取 FrameGraph 物理资源的回调作用域内暴露 26.3 世界目标。 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements LevelRendererAccessor {
    /** 原版创建并随每帧更新的目标句柄集合，不在帧图构建阶段解引用。 */
    @Shadow @Final
    private LevelTargetBundle targets;

    /** 当前同步主 pass 的只读资源快照，异常退出也必须清除。 */
    @Unique
    private Map<String, RenderTarget> availableTargets = Map.of();

    /** @return 原版共享渲染缓冲 */
    @Accessor("renderBuffers")
    public abstract RenderBuffers getRenderBuffers();

    @Override
    public @NotNull RenderBuffers renderBuffers() {
        return getRenderBuffers();
    }

    @Override
    public @NotNull Map<String, RenderTarget> availableFrameTargets() {
        return availableTargets;
    }

    /**
     * 包裹 FrameGraph 的同步回调，而非提前读取尚未分配的内部目标。
     *
     * <p>精确 26.3 addMainPass 在 OIT 分支为下列目标声明 readsAndWrites，
     * 进入回调时它们已取得物理资源；finally 保证异常退出也不会把资源快照带到下一帧。
     */
    @WrapOperation(
            method = "addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V")
    )
    private void scopeMainTargets(FramePass pass, Runnable execute, Operation<Void> original,
                                  @Local(name = "useImprovedTransparency") boolean useOit) {
        original.call(pass, (Runnable) () -> {
            availableTargets = Map.of();
            try {
                Map<String, RenderTarget> current = new LinkedHashMap<>();
                addAcquired(current, "main", targets.main);
                if (useOit) {
                    addAcquired(current, "oit_accumulate", targets.accumulate);
                    addAcquired(current, "oit_depth_bounds", targets.depthBounds);
                    addAcquired(current, "oit_depth_bounds_culled", targets.depthBoundsCulled);
                    for (int index = 0; index < targets.transmittance.size(); index++) {
                        addAcquired(current, "oit_transmittance_" + index, targets.transmittance.get(index));
                    }
                }
                availableTargets = Map.copyOf(current);
                execute.run();
            } finally {
                availableTargets = Map.of();
            }
        });
    }

    /**
     * 只接收已由当前 pass 声明并获取的句柄；无效句柄不解引用。
     *
     * @param current 当前回调的用途到物理目标映射
     * @param purpose 此目标在帧图中的具体用途
     * @param handle 已知属于当前 pass 依赖的句柄
     */
    @Unique
    private static void addAcquired(Map<String, RenderTarget> current, String purpose,
                                    ResourceHandle<RenderTarget> handle) {
        if (handle != null && handle != ResourceHandle.<RenderTarget>invalid()) {
            current.put(purpose, handle.get());
        }
    }
}
