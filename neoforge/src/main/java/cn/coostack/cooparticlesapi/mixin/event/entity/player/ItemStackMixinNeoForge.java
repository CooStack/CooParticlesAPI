package cn.coostack.cooparticlesapi.mixin.event.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerItemDestroyEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class ItemStackMixinNeoForge {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    public void onBreak(int p_220158_, ServerLevel level, LivingEntity entity, Consumer<Item> p_348596_, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(entity instanceof Player player)) {
            return;
        }
        var before = stack.copy();
        var event = new PlayerItemDestroyEvent(player, before);
        CooEventBus.call(event);
    }
}
