package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet;
import cn.coostack.cooparticlesapi.display.CooRenderTypePipelineFactory;
import cn.coostack.cooparticlesapi.test.options.display.MCShaders;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 将 Coo 粒子、描述符与实体发光管线加入原版资源加载阶段的预编译列表。 */
@Mixin(RenderPipelines.class)
public abstract class ParticleRenderPipelinesMixin {
    /** 保留原版顺序并追加模组命名空间下的粒子管线。 */
    @Inject(method = "requiredPipelines()Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void addParticlePipelines(CallbackInfoReturnable<List<RenderPipeline>> cir) {
        List<RenderPipeline> pipelines = new ArrayList<>(cir.getReturnValue());
        pipelines.addAll(CooParticleTextureSheet.getPipelines());
        pipelines.addAll(MCShaders.INSTANCE.pipelines());
        pipelines.addAll(CooRenderTypePipelineFactory.INSTANCE.pipelines());
        cir.setReturnValue(pipelines);
    }
}
