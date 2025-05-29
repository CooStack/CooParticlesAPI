package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.CooParticleAPI;
import cn.coostack.cooparticlesapi.utils.ParticleAsyncRenderHelper;
import com.google.common.collect.EvictingQueue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Queue;

/**
 * TODO
 * 异步生成BuiltBuffer时
 * 在主线程draw会导致 buffer no longer valid的异常
 * 原理未知
 */
@Mixin(ParticleManager.class)
public class ParticleManagerRenderMixin {
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
        ParticleAsyncRenderHelper.INSTANCE
                .renderParticlesAsync(array, instance, textureManager, camera, tickDelta);
        // 中断原来的判断
        return null;
    }
}
