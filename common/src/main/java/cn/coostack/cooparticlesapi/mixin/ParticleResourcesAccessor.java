package cn.coostack.cooparticlesapi.mixin;

import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 26.3 中由资源管理器持有的精灵集，不改变其重载和所有权。
 */
@Mixin(ParticleResources.class)
public interface ParticleResourcesAccessor {
    /**
     * @return 粒子类型资源 ID 到动态图集帧集的映射；调用方只读
     */
    @Accessor("spriteSets")
    Map<Identifier, ? extends SpriteSet> getSpriteSets();
}
