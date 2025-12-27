package cn.coostack.cooparticlesapi.mixin.events.entity.player;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerDisconnectEvent;
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerLoggedInEvent;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    public void onPlacePlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        var event = new PlayerLoggedInEvent(player);
        CooEventBus.call(event);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    public void onRemove(ServerPlayer player, CallbackInfo ci) {
        var event = new PlayerDisconnectEvent(player);
        CooEventBus.call(event);
    }
}
