package cn.coostack.cooparticlesapi.mixin.events.entity;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.EntityPrePlaceBlockEvent;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.world.entity.monster.EnderMan.EndermanLeaveBlockGoal"})
public class EnderManMixin {
    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "tick",
            cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/EnderMan$EndermanLeaveBlockGoal;canPlaceBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z"))
    public void onTick(CallbackInfo ci, @Local(name = "blockPos") BlockPos pos, @Local(name = "level") Level level) {
        var event = new EntityPrePlaceBlockEvent(enderman, level, pos, Direction.UP);
        if (CooEventBus.call(event).isCancelled()) {
            ci.cancel();
        }
    }

}
