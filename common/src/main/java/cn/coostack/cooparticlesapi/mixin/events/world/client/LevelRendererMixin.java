package cn.coostack.cooparticlesapi.mixin.events.world.client;

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements LevelRendererAccessor {
    @Accessor("renderBuffers")
    public abstract RenderBuffers getRenderBuffers();

    @Override
    public @NotNull RenderBuffers renderBuffers() {
        return getRenderBuffers();
    }
}
