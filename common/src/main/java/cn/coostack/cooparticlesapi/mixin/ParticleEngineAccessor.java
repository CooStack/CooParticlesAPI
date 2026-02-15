package cn.coostack.cooparticlesapi.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Queue;

/**
 * @author CooStack
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor
    Map<ParticleRenderType, Queue<Particle>> getParticles();

    @Accessor
    Queue<Particle> getParticlesToAdd();

    @Accessor
    Queue<TrackingEmitter> getTrackingEmitters();

    @Accessor
    Object2IntOpenHashMap<ParticleGroup> getTrackedParticleCounts();
}
