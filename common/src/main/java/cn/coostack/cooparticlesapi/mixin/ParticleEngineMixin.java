package cn.coostack.cooparticlesapi.mixin;


import cn.coostack.cooparticlesapi.particles.ControlableParticleGroup;
import cn.coostack.cooparticlesapi.particles.ControlableParticle;
import cn.coostack.cooparticlesapi.particles.CooParticleTextureSheet;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 将 Coo 图层接入 26.3 粒子分组，并在驱逐和清空时释放控制器。
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    /** 当前世界中按图层分类的活动粒子组。 */
    @Final
    @Shadow
    private Map<ParticleRenderType, ParticleGroup<?>> particles;

    /** 下次 tick 待入组的粒子，清空时也必须通知控制器。 */
    @Final
    @Shadow
    private Queue<Particle> particlesToAdd;

    /** 原版提取阶段遍历的图层顺序，同时接纳 Coo 注册图层。 */
    @Shadow
    @Final
    @Mutable
    private static List<ParticleRenderType> RENDER_ORDER;

    /** 使用 Coo 队列替换自定义图层的原版默认四边形组。 */
    @Inject(method = "createParticleGroup(Lnet/minecraft/client/particle/ParticleRenderType;)Lnet/minecraft/client/particle/ParticleGroup;", at = @At("RETURN"), cancellable = true)
    private void createControlableParticleGroup(
            ParticleRenderType type,
            CallbackInfoReturnable<ParticleGroup<?>> cir
    ) {
        if (CooParticleTextureSheet.getSheets().contains(type)) {
            if (!RENDER_ORDER.contains(type)) {
                var newOrder = new ArrayList<>(RENDER_ORDER);
                newOrder.add(type);
                RENDER_ORDER = newOrder;
            }
            cir.setReturnValue(new ControlableParticleGroup((ParticleEngine) (Object) this, type));
        }
    }

    /** 在原版丢弃队列前释放活动粒子和待入组粒子的控制器。 */
    @Inject(method = "clearParticles()V", at = @At("HEAD"))
    public void onClear(CallbackInfo ci) {
        particles.values().forEach(group -> {
            if (group instanceof ControlableParticleGroup controlableGroup) {
                controlableGroup.disposeParticles();
            }
        });
        particlesToAdd.forEach(particle -> {
            if (particle instanceof ControlableParticle controlable) {
                controlable.removeFromQueue();
            }
        });
    }

    /** 在原版列表之后追加内置 Coo 图层，保持原版图层的相对顺序。 */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onStaticRenderOrderInit(CallbackInfo ci) {
        var newOrder = new ArrayList<>(RENDER_ORDER);
        newOrder.addAll(
                CooParticleTextureSheet.getSheets()
        );

        RENDER_ORDER = newOrder;
    }

}
