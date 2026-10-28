package cn.coostack.cooparticlesapi.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 只在执行中的 OIT terrain 调用范围读取原版目标，不跨帧保存 FrameGraph 资源。 */
@Mixin(OitRenderPassProvider.Parameters.class)
public interface OitParametersAccessor {
    /** @return 当前阶段的深度范围纹理视图 */
    @Accessor("depthBoundsTargetView")
    GpuTextureView depthBoundsTargetView();

    /** @return 当前帧与原版共享的透射系数纹理视图 */
    @Accessor("transmittanceTargetViews")
    GpuTextureView[] transmittanceTargetViews();
}
