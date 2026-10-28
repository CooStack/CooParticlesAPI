package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class TestControllerPickMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void cooparticlesapi$releaseTestControllerPickUse(
            long window,
            MouseButtonInfo button,
            int action,
            CallbackInfo ci
    ) {
        if (button.button() == InputConstants.MOUSE_BUTTON_RIGHT && action == InputConstants.RELEASE) {
            TestControllerPickClient.releaseUseSuppression();
        }
    }
}
