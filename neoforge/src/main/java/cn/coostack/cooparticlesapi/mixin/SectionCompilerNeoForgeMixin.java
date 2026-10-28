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
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.List;

/** 对接 NeoForge 26.3 的五参数区段编译入口，保留自定义流体与额外几何回调。 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerNeoForgeMixin {
    /** 原版叶片不透明设置，与 NeoForge 模型输出选择一致。 */
    @Shadow @Final private boolean cutoutLeaves;

    /** 仅包装实际五参数重载，以 finally 回收异常退出的数据，避免桥接入口重复开始。 */
    @WrapMethod(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;" +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)" +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
    private SectionCompiler.Results compileOverlayBuild(SectionPos section, RenderSectionRegion region, VertexSorting sorting,
                                                       SectionBufferBuilderPack buffers,
                                                       List<AddSectionGeometryEvent.AdditionalSectionRenderer> renderers,
                                                       Operation<SectionCompiler.Results> original) {
        VanillaTerrainOverlay.beginBuild(buffers);
        try {
            SectionCompiler.Results result = original.call(section, region, sorting, buffers, renderers);
            VanillaTerrainOverlay.finishBuild(result);
            return result;
        } finally {
            VanillaTerrainOverlay.cancelBuild(buffers);
        }
    }

    /** 只在实际取块的重载中更新流体上下文，额外几何仍由 NeoForge 原回调负责。 */
    @WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;" +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)" +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState selectOverlayBlock(RenderSectionRegion region, BlockPos position, Operation<BlockState> original) {
        BlockState state = original.call(region, position);
        VanillaTerrainOverlay.currentBlock(position, state);
        return state;
    }

    /** NeoForge 自定义流体 renderFluid 与原版 tesselate 共用此输出，不能只包装默认分支。 */
    @ModifyVariable(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;" +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)" +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At("STORE"), ordinal = 0)
    private FluidRenderer.Output decorateFluidOutput(FluidRenderer.Output output) {
        return VanillaTerrainOverlay.fluidOutput(output);
    }

    /** 使用 NeoForge 已选定模型的 quad 输出，不复刻 ModelData 或额外模型解析。 */
    @WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;" +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)" +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "INVOKE",
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
