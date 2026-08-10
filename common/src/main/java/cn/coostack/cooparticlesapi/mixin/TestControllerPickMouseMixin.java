package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.test.block.client.TestControllerPickClient;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class TestControllerPickMouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void cooparticlesapi$releaseTestControllerPickUse(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo ci
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE) {
            TestControllerPickClient.releaseUseSuppression();
        }
    }
}
