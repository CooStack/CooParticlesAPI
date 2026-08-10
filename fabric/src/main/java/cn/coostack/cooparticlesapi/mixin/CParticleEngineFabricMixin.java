package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class CParticleEngineFabricMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;turnOffLightLayer()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void cooParticlesAPI$renderGpuParticles(LightTexture lightTexture,
                                                    Camera camera,
                                                    float partialTick,
                                                    CallbackInfo info) {
        CParticleSystemManager.renderFabricParticlePass(camera, partialTick);
    }
}
