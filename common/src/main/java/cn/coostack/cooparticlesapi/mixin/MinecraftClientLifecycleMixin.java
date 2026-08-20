package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.coofx.client.CooFXClient;
import cn.coostack.cooparticlesapi.performance.client.PerformanceStatusClientController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在客户端 OpenGL 上下文销毁前释放跨 loader 共用的 CooFX 资源。 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientLifecycleMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void cooparticlesapi$stopCooFxClient(CallbackInfo callbackInfo) {
        PerformanceStatusClientController.shutdown();
        CooFXClient.stopClient();
    }
}
