package cn.coostack.cooparticlesapi.mixin.events;

import cn.coostack.cooparticlesapi.event.CooEventBus;
import cn.coostack.cooparticlesapi.event.events.client.ClientStoppingEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在图形上下文仍有效时发布客户端关闭事件。 */
@Mixin(Minecraft.class)
public abstract class StructureClientLifecycleMixin {
    /** 停止监听器须同步释放 GPU 资源，不延迟到线程退出之后。 */
    @Inject(method = "close", at = @At("HEAD"))
    private void stop(CallbackInfo callback) {
        CooEventBus.call(new ClientStoppingEvent());
    }
}
