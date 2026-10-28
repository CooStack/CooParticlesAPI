package cn.coostack.cooparticlesapi.mixin.event.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerItemDestroyEvent;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** 从 Fabric 的真实损坏回调发布物品销毁事件。 */
@Mixin(ItemStack.class)
public class ItemStackMixinFabric {

    /** 仅在原版确认损坏并调用通知时发布事件，保留缩减前的原版快照。 */
    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void onBreak(int damage, ServerPlayer player, Consumer<ItemStack> onBreak, CallbackInfo ci,
                         @Local(name = "broken") ItemStack broken) {
        if (player != null) CooEventBus.call(new PlayerItemDestroyEvent(player, broken.copy()));
    }
}
