package cn.coostack.cooparticlesapi.mixin.compat.iris;

import cn.coostack.cooparticlesapi.compat.iris.RenderTypeIrisSupposerRegistry;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderInstance.class, priority = 900)
public abstract class ShaderInstanceIrisCompatMixin {
    @Shadow
    @Final
    private String name;

    @Dynamic("Injected by Iris into ShaderInstance")
    @SuppressWarnings("target")
    @Inject(method = "iris$shouldSkipThis()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void cooparticlesapi$allowRegisteredShaderForIris(CallbackInfoReturnable<Boolean> cir) {
        if (RenderTypeIrisSupposerRegistry.shouldAllowShader(this.name)) {
            cir.setReturnValue(false);
        }
    }
}
