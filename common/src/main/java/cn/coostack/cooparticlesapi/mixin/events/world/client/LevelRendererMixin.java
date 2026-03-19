package cn.coostack.cooparticlesapi.mixin.events.world.client;

import cn.coostack.cooparticlesapi.accessor.LevelRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements LevelRendererAccessor {
    @Accessor("renderBuffers")
    public abstract RenderBuffers getRenderBuffers();

    @Accessor("translucentTarget")
    public abstract @Nullable RenderTarget getTranslucentTarget();

    @Accessor("itemEntityTarget")
    public abstract @Nullable RenderTarget getItemEntityTarget();

    @Accessor("particlesTarget")
    public abstract @Nullable RenderTarget getParticlesTarget();

    @Accessor("weatherTarget")
    public abstract @Nullable RenderTarget getWeatherTarget();

    @Accessor("cloudsTarget")
    public abstract @Nullable RenderTarget getCloudsTarget();

    @Accessor("transparencyChain")
    public abstract @Nullable PostChain getTransparencyChain();

    @Override
    public @NotNull RenderBuffers renderBuffers() {
        return getRenderBuffers();
    }

    @Override
    public @Nullable RenderTarget translucentTarget() {
        return getTranslucentTarget();
    }

    @Override
    public @Nullable RenderTarget itemEntityTarget() {
        return getItemEntityTarget();
    }

    @Override
    public @Nullable RenderTarget particlesTarget() {
        return getParticlesTarget();
    }

    @Override
    public @Nullable RenderTarget weatherTarget() {
        return getWeatherTarget();
    }

    @Override
    public @Nullable RenderTarget cloudsTarget() {
        return getCloudsTarget();
    }

    @Override
    public boolean hasTransparencyChain() {
        return getTransparencyChain() != null;
    }
}
