package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.compat.iris.CooIrisNativePipelineDraw;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 在原版仍持有有效 RenderPass 与暂存网格时，接入 Coo 自有片元的 Iris 目标。
 * 不改变普通管线，不重建或延迟 GPU 缓冲，不覆盖原版动态绑定。
 */
@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeCooIrisMixin {
    /** 用身份表限制拦截范围；桥接不接管时原样执行原版。 */
    @WrapMethod(method = "draw")
    private void drawCooNativePipeline(
            StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass,
            RenderPipeline pipeline, Operation<Void> original
    ) {
        if (!CooIrisNativePipelineDraw.draw(pipeline, renderPass,
                nativePipeline -> original.call(info, renderPass, nativePipeline))) {
            original.call(info, renderPass, pipeline);
        }
    }
}
