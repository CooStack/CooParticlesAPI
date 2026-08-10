package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class TestControllerPickUseMixin {
    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void cooparticlesapi$consumeTestControllerPick(CallbackInfo ci) {
        if (TestControllerPickClient.consumePickUseInput()) {
            this.rightClickDelay = 4;
            ci.cancel();
        }
    }
}
