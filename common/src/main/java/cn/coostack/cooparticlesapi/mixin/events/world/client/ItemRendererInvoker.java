package cn.coostack.cooparticlesapi.mixin.events.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemRenderer.class)
public interface ItemRendererInvoker {

    @Invoker("renderModelLists")
    void renderModel(@NotNull BakedModel model, @NotNull ItemStack stack, int combinedLight, int combinedOverlay, @NotNull PoseStack poseStack, @NotNull VertexConsumer consumer);
}
