package cn.coostack.cooparticlesapi.mixin.structure;

import cn.coostack.cooparticlesapi.entities.structure.ModelItemBehavior;
import cn.coostack.cooparticlesapi.entities.structure.StructureModels;
import cn.coostack.cooparticlesapi.entities.structure.client.StructureModelClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在两个加载器共用的物品渲染入口替换模型外观，保留原物品使用行为。 */
@Mixin(ItemRenderer.class)
public abstract class StructureItemRendererMixin {
    /** 模型载体与真实物品外观共用同一变换和缓存。 */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(ItemStack stack, ItemDisplayContext mode, boolean leftHanded, PoseStack matrices,
                        MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo callback) {
        if (stack.isEmpty() || (!stack.is(StructureModels.INSTANCE.getHELD_MODEL()) && !ModelItemBehavior.isSkinned(stack))) return;
        ItemRenderer renderer = (ItemRenderer) (Object) this;
        matrices.pushPose();
        try {
            renderer.getItemModelShaper().getItemModel(new ItemStack(StructureModels.INSTANCE.getHELD_MODEL()))
                    .getTransforms().getTransform(mode).apply(leftHanded, matrices);
            matrices.translate(-0.5F, -0.5F, -0.5F);
            StructureModelClient.INSTANCE.getItemRenderer().render(stack, mode, matrices, buffers, light, overlay);
        } finally {
            matrices.popPose();
        }
        callback.cancel();
    }
}
