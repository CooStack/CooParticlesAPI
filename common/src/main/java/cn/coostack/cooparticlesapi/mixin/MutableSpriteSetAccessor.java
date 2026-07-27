package cn.coostack.cooparticlesapi.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes the ordered frames used by SpriteSet#get(age, lifetime). */
@Mixin(targets = "net.minecraft.client.particle.ParticleEngine$MutableSpriteSet")
public interface MutableSpriteSetAccessor {
    @Accessor("sprites")
    List<TextureAtlasSprite> cooparticlesapi$getSprites();
}
