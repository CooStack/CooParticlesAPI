package cn.coostack.cooparticlesapi.mixin.structure;

import cn.coostack.cooparticlesapi.entities.structure.ModelItemBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 真实物品使用后追加模型通知，不改物品类型、原生逻辑及原有消费规则。 */
@Mixin(ItemStack.class)
public abstract class StructureItemStackMixin {
    /** 空气使用成功后传递外观；先复制输入以兼容桶或药水返回新栈。 */
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void beforeUse(Level level, Player player, InteractionHand hand,
                           CallbackInfoReturnable<InteractionResult> callback,
                           @Share("structureBeforeUse") LocalRef<ItemStack> before) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ModelItemBehavior.isSkinned(stack)) before.set(stack.copyWithCount(1));
    }

    /** 在原版应用容器与冷却组件后读取最终返回栈，避免对中间栈转移外观。 */
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void afterUse(Level level, Player player, InteractionHand hand,
                          CallbackInfoReturnable<InteractionResult> callback,
                          @Share("structureBeforeUse") LocalRef<ItemStack> before) {
        ItemStack original = before.get();
        if (original == null || !callback.getReturnValue().consumesAction()) return;
        if (callback.getReturnValue() instanceof InteractionResult.Success success) {
            ItemStack result = success.heldItemTransformedTo();
            ModelItemBehavior.transferAppearance(original, result != null ? result : (ItemStack) (Object) this);
        }
        ModelItemBehavior.used(original, level, player, hand);
    }

    /** 保存入口栈，兼容 NeoForge 将原版调用替换为放置事务钩子的实现。 */
    @Inject(method = "useOn", at = @At("HEAD"))
    private void beforeUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback,
                             @Share("structureBeforeUseOn") LocalRef<ItemStack> before) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ModelItemBehavior.isSkinned(stack)) before.set(stack.copyWithCount(1));
    }

    /** 整个方块使用事务成功后才通知；PASS 或 NeoForge 取消时不通知。 */
    @Inject(method = "useOn", at = @At("RETURN"))
    private void afterUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback,
                            @Share("structureBeforeUseOn") LocalRef<ItemStack> before) {
        if (before.get() != null && callback.getReturnValue().consumesAction() && context.getPlayer() != null) {
            ModelItemBehavior.used(before.get(), context.getLevel(), context.getPlayer(), context.getHand());
        }
    }

    /** 活体交互成功后发通知，不再额外调用物品使用。 */
    @WrapOperation(method = "interactLivingEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;interactLivingEntity(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult interact(Item item, ItemStack stack, Player player, LivingEntity target,
                                       InteractionHand hand, Operation<InteractionResult> original) {
        if (!ModelItemBehavior.isSkinned(stack)) return original.call(item, stack, player, target, hand);
        ItemStack before = stack.copyWithCount(1);
        InteractionResult result = original.call(item, stack, player, target, hand);
        if (result.consumesAction()) ModelItemBehavior.used(before, player.level(), player, hand);
        return result;
    }

    /** 完整使用后由原版产生容器，模型只转移外观和发布完成事件。 */
    @Inject(method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private void beforeFinish(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> callback,
                              @Share("structureBeforeFinish") LocalRef<ItemStack> before) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ModelItemBehavior.isSkinned(stack)) before.set(stack.copyWithCount(1));
    }

    /** 完成原版使用及 USE_REMAINDER 交换后只转移最终结果，不重复消费或发布事件。 */
    @Inject(method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void afterFinish(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> callback,
                             @Share("structureBeforeFinish") LocalRef<ItemStack> before) {
        if (before.get() == null) return;
        ModelItemBehavior.transferAppearance(before.get(), callback.getReturnValue());
        ModelItemBehavior.finished(before.get(), level, user);
    }
}
