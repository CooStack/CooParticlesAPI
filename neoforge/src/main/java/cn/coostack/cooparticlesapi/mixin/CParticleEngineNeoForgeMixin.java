package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass;
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(ParticleEngine.class)
public abstract class CParticleEngineNeoForgeMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;turnOffLightLayer()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void cooParticlesAPI$renderGpuParticles(LightTexture lightTexture,
                                                    Camera camera,
                                                    float partialTick,
                                                    Frustum frustum,
                                                    Predicate<ParticleRenderType> renderTypeFilter,
                                                    CallbackInfo info) {
        boolean opaque = renderTypeFilter.test(ParticleRenderType.PARTICLE_SHEET_OPAQUE)
                || renderTypeFilter.test(ParticleRenderType.PARTICLE_SHEET_LIT)
                || renderTypeFilter.test(ParticleRenderType.TERRAIN_SHEET);
        boolean translucent = renderTypeFilter.test(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT);
        CParticleRenderPass pass = CParticleRenderPass.fromCoverage(opaque, translucent);
        CParticleSystemManager.renderParticlePass(camera, partialTick, pass);
    }
}
