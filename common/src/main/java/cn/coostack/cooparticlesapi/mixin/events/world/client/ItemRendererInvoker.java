package cn.coostack.cooparticlesapi.mixin.events.world.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemRenderer.class)
public interface ItemRendererInvoker {

    /**
     * Returns the item color registry owned by the renderer.
     *
     * <p>Use: resolve one representative particle-icon tint with
     * {@code ((ItemRendererInvoker) renderer).cooparticlesapi$getItemColors()}.
     * Do not retain or mutate the returned registry.</p>
     *
     * @return the renderer's live item color registry
     */
    @Accessor("itemColors")
    ItemColors cooparticlesapi$getItemColors();

    @Invoker("renderModelLists")
    void renderModel(@NotNull BakedModel model, @NotNull ItemStack stack, int combinedLight, int combinedOverlay, @NotNull PoseStack poseStack, @NotNull VertexConsumer consumer);
}
