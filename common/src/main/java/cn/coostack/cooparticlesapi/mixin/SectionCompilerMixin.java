package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPipelineManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"
            )
    )
    private void cooParticlesAPI$writeTerrainFluidOverlayBatch(
            BlockRenderDispatcher dispatcher,
            BlockPos pos,
            BlockAndTintGetter level,
            VertexConsumer consumer,
            BlockState state,
            FluidState fluidState,
            Operation<Void> original,
            @Local Map<RenderType, BufferBuilder> renderedLayers,
            @Local(argsOnly = true) SectionBufferBuilderPack sectionBufferBuilderPack
    ) {
        RenderType baseLayer = ItemBlockRenderTypes.getRenderLayer(fluidState);
        RenderType overlayLayer = CooTerrainPipelineManager.resolveOverlayRenderType(state, baseLayer, pos);
        if (overlayLayer == null || CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
            original.call(dispatcher, pos, level, consumer, state, fluidState);
        }
        if (overlayLayer == null) {
            return;
        }
        BufferBuilder overlayBuffer = renderedLayers.computeIfAbsent(
                overlayLayer,
                key -> new BufferBuilder(
                        sectionBufferBuilderPack.buffer(key),
                        key.mode(),
                        CooTerrainPipelineManager.vertexFormat(key, key.format())
                )
        );
        VertexConsumer overlayConsumer = CooTerrainPipelineManager.decorateVertexConsumer(
                overlayLayer,
                pos,
                overlayBuffer
        );
        original.call(dispatcher, pos, level, overlayConsumer, state, fluidState);
    }

    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V"
            ),
            require = 0
    )
    private void cooParticlesAPI$writeTerrainOverlayBatch(
            BlockRenderDispatcher dispatcher,
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            Operation<Void> original,
            @Local Map<RenderType, BufferBuilder> renderedLayers,
            @Local(argsOnly = true) SectionBufferBuilderPack sectionBufferBuilderPack
    ) {
        RenderType baseLayer = ItemBlockRenderTypes.getChunkRenderType(state);
        RenderType overlayLayer = CooTerrainPipelineManager.resolveOverlayRenderType(state, baseLayer, pos);
        if (overlayLayer == null || CooTerrainPipelineManager.shouldPreserveVanillaTerrainGeometry()) {
            poseStack.pushPose();
            try {
                original.call(dispatcher, state, pos, level, poseStack, consumer, checkSides, random);
            } finally {
                poseStack.popPose();
            }
        }
        if (overlayLayer == null) {
            return;
        }
        BufferBuilder overlayBuffer = renderedLayers.computeIfAbsent(
                overlayLayer,
                key -> new BufferBuilder(
                        sectionBufferBuilderPack.buffer(key),
                        key.mode(),
                        CooTerrainPipelineManager.vertexFormat(key, key.format())
                )
        );
        VertexConsumer overlayConsumer = CooTerrainPipelineManager.decorateVertexConsumer(
                overlayLayer,
                pos,
                overlayBuffer
        );
        random.setSeed(state.getSeed(pos));
        poseStack.pushPose();
        try {
            original.call(dispatcher, state, pos, level, poseStack, overlayConsumer, checkSides, random);
        } finally {
            poseStack.popPose();
        }
    }

    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object cooParticlesAPI$sortTranslucentTerrainPipeline(
            Map<RenderType, MeshData> renderedLayers,
            Object renderTypeObject,
            Object meshDataObject,
            Operation<Object> original,
            @Local(argsOnly = true) VertexSorting vertexSorting,
            @Local(argsOnly = true) SectionBufferBuilderPack sectionBufferBuilderPack
    ) {
        RenderType renderType = (RenderType) renderTypeObject;
        MeshData meshData = (MeshData) meshDataObject;
        if (CooTerrainPipelineManager.requiresTerrainSorting(renderType)) {
            meshData.sortQuads(sectionBufferBuilderPack.buffer(renderType), vertexSorting);
        }
        return original.call(renderedLayers, renderType, meshData);
    }
}
