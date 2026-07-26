package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineDebugMixin {
    @Inject(method = "countParticles", at = @At("RETURN"), cancellable = true)
    private void cooParticlesAPI$appendCParticleCount(CallbackInfoReturnable<String> info) {
        info.setReturnValue(info.getReturnValue() + " + " + CParticleSystemManager.totalAlive());
    }
}
