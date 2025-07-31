package cn.coostack.cooparticlesapi.mixin;


import cn.coostack.cooparticlesapi.CooParticleAPI;
import cn.coostack.cooparticlesapi.config.APIConfigManager;
import com.google.common.collect.EvictingQueue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Final
    @Shadow
    private Map<ParticleTextureSheet, Queue<Particle>> particles;

    @Final
    @Shadow
    private Queue<Particle> newParticles;

    @Shadow
    protected abstract void addTo(ParticleGroup group, int count);

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    public Object changeMaxParticles(Queue<Object> queue) {
        // 不需要判断 EnabledParticleCountInject 了，已在注入时判断，关这个一般都是因为会注入失败，所以不用担心
        Particle particle;
        int limit = APIConfigManager.getConfig().getParticleCountLimit();
        while ((particle = newParticles.poll()) != null) {
            Queue<Particle> queue1 = particles.computeIfAbsent(particle.getType(),
                    sheet -> EvictingQueue.create(limit));
            // limit 在程序生命周期内不会改变，这里可以直接判断
            if (queue1.size() == limit) {
                // 这样驱逐队列就没用了但是可以避免内存泄漏
                Particle poll = queue1.poll();
                if (poll != null) onEvict(poll);
            }
            queue1.add(particle);
        }
        return null;
    }

    @Inject(method = "clearParticles", at = @At("HEAD"))
    public void clearParticles(CallbackInfo ci) {
        // p可能为null (因为ParticleManagerAsyncMixin
        particles.values().forEach(q -> q.forEach((p) -> {
            if (p != null) onEvict(p);
        }));
        newParticles.forEach((p) -> {
            if (p != null) onEvict(p);
        });
        // 事件
        CooParticleAPI.INSTANCE.getLogger().info("ParticleManager: clearParticles invoked");
    }


    @Unique
    private void onEvict(Particle p) {
        // 这是原版 bug，但本模组会放大这个问题所以有必要修复一下
        // 判断 isAlive 以保证幂等性（其他模组可能注入类似方法）
        if (p.isAlive()) {
            p.markDead();
            p.getGroup().ifPresent(group -> addTo(group, -1));
        }
    }
}
