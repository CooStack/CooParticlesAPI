package cn.coostack.cooparticlesapi.mixin.event.world.server;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPostTickEvent;
import cn.coostack.cooparticlesapi.event.events.world.server.ServerWorldPreTickEvent;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerWorldMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(CallbackInfo ci) {
        var self = (ServerLevel) (Object) this;
        var server = self.getServer();
        CooEventBus.call(new ServerWorldPreTickEvent(self, server));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(CallbackInfo ci) {
        var self = (ServerLevel) (Object) this;
        var server = self.getServer();
        CooEventBus.call(new ServerWorldPostTickEvent(self, server));
    }

}
