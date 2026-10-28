package cn.coostack.cooparticlesapi.mixin.structure;

import cn.coostack.cooparticlesapi.entities.structure.ModelItemBehavior;
import cn.coostack.cooparticlesapi.entities.structure.StructureModels;
import cn.coostack.cooparticlesapi.entities.structure.client.HeldStructureItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在两个加载器共用的物品渲染入口替换模型外观，保留原物品使用行为。 */
@Mixin(ItemModelResolver.class)
public abstract class StructureItemRendererMixin {
    /** 模型载体与真实物品外观共用同一变换和缓存。 */
    @Inject(
            method = "appendItemLayers(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
            at = @At("HEAD"), cancellable = true
    )
    private void render(ItemStackRenderState output, ItemStack stack, ItemDisplayContext mode,
                        Level level, ItemOwner owner, int seed, CallbackInfo callback) {
        if (stack.isEmpty() || (!stack.is(StructureModels.INSTANCE.getHELD_MODEL()) && !ModelItemBehavior.isSkinned(stack))) return;
        HeldStructureItemModel.INSTANCE.append(output, stack, mode);
        callback.cancel();
    }
}
