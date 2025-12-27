package cn.coostack.cooparticlesapi.mixin.events.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerBlockBreakEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    public void onDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // 叫事件
        var state = level.getBlockState(pos);
        var event = new PlayerBlockBreakEvent(player, level, pos, state);

        if (CooEventBus.call(event).isCancelled()) {
            cir.setReturnValue(false);
            player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        }
    }
}
