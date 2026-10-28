package cn.coostack.cooparticlesapi.mixin.event.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerItemDestroyEvent;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** 从 NeoForge 的活体损坏回调发布玩家物品销毁事件。 */
@Mixin(ItemStack.class)
public class ItemStackMixinNeoForge {

    /** 按 NeoForge 扩展的活体参数捕获真实损坏通知，不对普通耐久变化误报。 */
    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void onBreak(int damage, LivingEntity entity, Consumer<ItemStack> onBreak, CallbackInfo ci,
                         @Local(name = "broken") ItemStack broken) {
        if (entity instanceof Player player) CooEventBus.call(new PlayerItemDestroyEvent(player, broken.copy()));
    }
}
