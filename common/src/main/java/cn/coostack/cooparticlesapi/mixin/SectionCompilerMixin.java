package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.VanillaTerrainOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 在 26.3 原版区段编译输出处复制覆盖几何，保持原版模型求值与光照。 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    /** 原版是否把叶片强制放入不透明层，与原输出选择保持一致。 */
    @Shadow @Final private boolean cutoutLeaves;

    /** 编译成功时转交所有权，异常退出时也必须立即清除线程绑定和未完成的 CPU 内存。 */
    @WrapMethod(method = "compile")
    private SectionCompiler.Results compileOverlayBuild(SectionPos section, RenderSectionRegion region, VertexSorting sorting,
                                                       SectionBufferBuilderPack buffers,
                                                       Operation<SectionCompiler.Results> original) {
        VanillaTerrainOverlay.beginBuild(buffers);
        try {
            SectionCompiler.Results result = original.call(section, region, sorting, buffers);
            VanillaTerrainOverlay.finishBuild(result);
            return result;
        } finally {
            VanillaTerrainOverlay.cancelBuild(buffers);
        }
    }

    /** 把当前方块上下文复制给流体输出，避免保存原版可变坐标。 */
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState selectOverlayBlock(RenderSectionRegion region, BlockPos position, Operation<BlockState> original) {
        BlockState state = original.call(region, position);
        VanillaTerrainOverlay.currentBlock(position, state);
        return state;
    }

    /** 一次包装同时覆盖普通流体和同输出提供者上的后续渲染调用。 */
    @ModifyVariable(method = "compile", at = @At("STORE"), ordinal = 0)
    private FluidRenderer.Output decorateFluidOutput(FluidRenderer.Output output) {
        return VanillaTerrainOverlay.fluidOutput(output);
    }

    /** 已完成的 quad 仅复制输出，不重新运行模型或改变随机数状态。 */
    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(" +
                    "Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFF" +
                    "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;" +
                    "Lnet/minecraft/world/level/block/state/BlockState;" +
                    "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void decorateBlockOutput(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z,
                                     BlockAndTintGetter region, BlockPos position, BlockState state,
                                     BlockStateModel model, long seed, Operation<Void> original) {
        original.call(renderer, VanillaTerrainOverlay.quadOutput(
                output, state, position, ModelBlockRenderer.forceOpaque(cutoutLeaves, state)
        ), x, y, z, region, position, state, model, seed);
    }

}
