package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(SectionBufferBuilderPack.class)
public abstract class SectionBufferBuilderPackMixin {
    @Shadow
    @Final
    private Map<RenderType, ByteBufferBuilder> buffers;

    @Inject(method = "buffer", at = @At("HEAD"), cancellable = true)
    private void cooParticlesAPI$allocateTerrainPipelineBuffer(
            RenderType renderType,
            CallbackInfoReturnable<ByteBufferBuilder> callback
    ) {
        if (!CooTerrainPipelineManager.isTerrainRenderType(renderType)) {
            return;
        }
        callback.setReturnValue(buffers.computeIfAbsent(
                renderType,
                key -> new ByteBufferBuilder(key.bufferSize())
        ));
    }
}
