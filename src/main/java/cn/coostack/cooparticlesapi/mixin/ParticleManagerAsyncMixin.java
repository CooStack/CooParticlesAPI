package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.utils.ParticleAsyncHelper;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Queue;

/**
 * TODO
 * 100%崩端 提示非法访问内存
 * 原因未知
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerAsyncMixin {
    @Redirect(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleTextureSheet;begin(Lnet/minecraft/client/render/Tessellator;Lnet/minecraft/client/texture/TextureManager;)Lnet/minecraft/client/render/BufferBuilder;"))
    public BufferBuilder renderParticles(ParticleTextureSheet instance,
                                         Tessellator tessellator,
                                         TextureManager textureManager,
                                         @Local(ordinal = 0) Queue<Particle> queue,
                                         @Local(argsOnly = true) Camera camera,
                                         @Local(argsOnly = true) float tickDelta,
                                         @Local(ordinal = 0) ParticleTextureSheet sheet) {
        Particle[] array = queue.toArray(new Particle[0]);
        // 异步调用...
        ParticleAsyncHelper.INSTANCE
                .renderParticlesAsync(array, instance, textureManager, camera, tickDelta);
        // 中断原来的判断
        return null;
    }

    @Shadow
    protected abstract void addTo(ParticleGroup group, int count);

    @Inject(method = "tickParticles", at = @At("HEAD"), cancellable = true)
    public void tickParticles(Collection<Particle> collection, CallbackInfo info) {
        if (collection instanceof Queue<Particle> queue) {
            info.cancel();
            Particle[] array = queue.toArray(new Particle[0]);
            ParticleAsyncHelper.INSTANCE
                    .tickParticlesAsync(array, collection, (g, c) -> {
                        addTo(g, c);
                        return null;
                    });
        }
    }


}
