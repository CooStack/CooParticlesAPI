package cn.coostack.cooparticlesapi.mixin.structure;

import cn.coostack.cooparticlesapi.entities.structure.ModelItemBehavior;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 为装水、挤奶等交换栈操作保留外观；创造模式保留原件时不复制模型库存。 */
@Mixin(ItemUtils.class)
public abstract class StructureItemExchangeMixin {
    /** 仅单件非创造输入允许向输出转移模型载荷。 */
    @Inject(method = "createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private static void transfer(ItemStack input, Player player, ItemStack output, boolean creative,
                                 CallbackInfoReturnable<ItemStack> callback) {
        if (!player.isCreative() && input.getCount() == 1) ModelItemBehavior.transferAppearance(input, output);
    }
}
