package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionRenderDispatcherRenderSectionMixin {
    @Unique
    private final Map<RenderType, VertexBuffer> cooParticlesAPI$terrainBuffers =
            Collections.synchronizedMap(new IdentityHashMap<>());

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$getTerrainBuffer(
            RenderType renderType,
            CallbackInfoReturnable<VertexBuffer> callback
    ) {
        if (!CooTerrainPipelineManager.isTerrainRenderType(renderType)) {
            return;
        }
        VertexBuffer buffer = cooParticlesAPI$terrainBuffers.get(renderType);
        if (buffer != null) {
            callback.setReturnValue(buffer);
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            callback.setReturnValue(cooParticlesAPI$createTerrainBuffer(renderType));
            return;
        }

        CompletableFuture<VertexBuffer> created = new CompletableFuture<>();
        RenderSystem.recordRenderCall(() -> {
            try {
                created.complete(cooParticlesAPI$createTerrainBuffer(renderType));
            } catch (Throwable error) {
                created.completeExceptionally(error);
            }
        });
        callback.setReturnValue(created.join());
    }

    @Unique
    private VertexBuffer cooParticlesAPI$createTerrainBuffer(RenderType renderType) {
        return cooParticlesAPI$terrainBuffers.computeIfAbsent(
                renderType,
                ignored -> new VertexBuffer(VertexBuffer.Usage.STATIC)
        );
    }

    @Inject(method = "releaseBuffers", at = @At("TAIL"))
    private void cooParticlesAPI$releaseTerrainBuffers(CallbackInfo callback) {
        synchronized (cooParticlesAPI$terrainBuffers) {
            cooParticlesAPI$terrainBuffers.values().forEach(VertexBuffer::close);
            cooParticlesAPI$terrainBuffers.clear();
        }
    }
}
