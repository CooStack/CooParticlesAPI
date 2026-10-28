package cn.coostack.cooparticlesapi.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

/**
 * 暴露粒子队列、限额与资源管理器，供客户端回收和图集解析使用。
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    /** @return 按渲染类型分类的活动粒子组 */
    @Accessor
    Map<ParticleRenderType, ParticleGroup<?>> getParticles();

    /** @return 下一次 tick 待入组的粒子队列 */
    @Accessor
    Queue<Particle> getParticlesToAdd();

    /** @return 跟踪实体发射器的队列 */
    @Accessor
    Queue<TrackingEmitter> getTrackingEmitters();

    /** @return 每个原版限额对应的当前占用数量 */
    @Accessor
    Object2IntOpenHashMap<ParticleLimit> getTrackedParticleCounts();

    /**
     * @return 26.3 中负责粒子提供器与图集帧的资源管理器
     */
    @Accessor("resourceManager")
    ParticleResources getParticleResources();

    /**
     * 保留图集消费者的旧入口，实际数据由 26.3 的资源管理器持有。
     *
     * @return 资源重载后更新的粒子类型到精灵集映射；调用方不得修改
     */
    default Map<Identifier, ? extends SpriteSet> getSpriteSets() {
        return ((ParticleResourcesAccessor) getParticleResources()).getSpriteSets();
    }
}
