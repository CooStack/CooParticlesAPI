package cn.coostack.cooparticlesapi.mixin.compat.iris;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 通过可选 Mixin 读取 Iris final pass 的真实颜色输出 framebuffer。 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.FinalPassRenderer", remap = false)
public interface FinalPassRendererAccessor {
    /** 获取 Iris 持有的 final 输出 framebuffer；调用方不得释放它。 */
    @Accessor("colorHolder")
    GlFramebuffer getColorHolder();
}
