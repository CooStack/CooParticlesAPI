package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient;
import net.minecraft.client.KeyboardHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class TestControllerPickKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cooparticlesapi$cancelTestControllerPick(
            long window,
            int action,
            KeyEvent event,
            CallbackInfo ci
    ) {
        if (event.key() == InputConstants.KEY_ESCAPE && action == InputConstants.PRESS && TestControllerPickClient.cancelAndReopenFromEsc()) {
            ci.cancel();
        }
    }
}
