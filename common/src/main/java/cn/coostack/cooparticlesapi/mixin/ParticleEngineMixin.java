package cn.coostack.cooparticlesapi.mixin;


import cn.coostack.cooparticlesapi.CooParticlesConstants;
import cn.coostack.cooparticlesapi.platform.CooParticlesServices;
import com.google.common.collect.EvictingQueue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/**
 * @author CooStack
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Final
    @Shadow
    private Map<ParticleRenderType, Queue<Particle>> particles;

    @Final
    @Shadow
    private Queue<Particle> particlesToAdd;

    @Shadow
    protected abstract void updateCount(ParticleGroup group, int count);

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    public Object changeMaxParticles(Queue<Object> queue) {
        // 不需要判断 EnabledParticleCountInject 了，已在注入时判断，关这个一般都是因为会注入失败，所以不用担心
        Particle particle;
        int limit = CooParticlesServices.API_CONFIG_MANAGER.getConfig().getParticleCountLimit();
        while ((particle = particlesToAdd.poll()) != null) {
            Queue<Particle> queue1 = particles.computeIfAbsent(particle.getRenderType(),
                    sheet -> EvictingQueue.create(limit));
            // limit 在程序生命周期内不会改变，这里可以直接判断
            if (queue1.size() == limit) {
                // 这样驱逐队列就没用了但是可以避免内存泄漏
                Particle poll = queue1.poll();
                if (poll != null) {
                    cooParticlesAPI$onEvict(poll);
                }
            }
            queue1.add(particle);
        }
        return null;
    }

    @Inject(method = "clearParticles", at = @At("HEAD"))
    public void onClear(CallbackInfo ci) {
        // p可能为null (因为ParticleManagerAsyncMixin
        particles.values().forEach(q -> q.forEach((p) -> {
            if (p != null) cooParticlesAPI$onEvict(p);
        }));
        particlesToAdd.forEach((p) -> {
            if (p != null) cooParticlesAPI$onEvict(p);
        });
        // 事件
        CooParticlesConstants.logger.info("ParticleManager: clearParticles invoked");
    }


    @Unique
    private void cooParticlesAPI$onEvict(Particle p) {
        // 这是原版 bug，但本模组会放大这个问题所以有必要修复一下
        // 判断 isAlive 以保证幂等性（其他模组可能注入类似方法）
        if (p.isAlive()) {
            p.remove();
            p.getParticleGroup().ifPresent(group -> updateCount(group, -1));
        }
    }
}
