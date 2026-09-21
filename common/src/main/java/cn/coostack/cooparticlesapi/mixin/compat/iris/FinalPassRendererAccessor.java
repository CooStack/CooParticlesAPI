package cn.coostack.cooparticlesapi.mixin.compat.iris;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FinalPassRenderer.class)
public interface FinalPassRendererAccessor {
    @Accessor
    GlFramebuffer getColorHolder();
}
