package cn.coostack.cooparticlesapi.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 暴露 SpriteSet 按年龄索引的有序图集帧，目标为 26.3 的资源管理器内部类型。 */
@Mixin(targets = "net.minecraft.client.particle.ParticleResources$MutableSpriteSet")
public interface MutableSpriteSetAccessor {
    /** @return 由资源重载更新的有序图集帧；调用方不得修改 */
    @Accessor("sprites")
    List<TextureAtlasSprite> getParticleSprites();
}
