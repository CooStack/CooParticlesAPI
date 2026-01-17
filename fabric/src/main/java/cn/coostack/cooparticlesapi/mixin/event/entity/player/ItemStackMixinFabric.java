package cn.coostack.cooparticlesapi.mixin.event.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerItemDestroyEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class ItemStackMixinFabric {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    public void onBreak(int damage, ServerLevel level, ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        var before = stack.copy();
        // 防止空指针异常
        if (before == null || player == null) {
            return;
        }
        var event = new PlayerItemDestroyEvent(player, before);
        CooEventBus.call(event);
    }
}
