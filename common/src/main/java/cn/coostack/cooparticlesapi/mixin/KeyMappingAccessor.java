package cn.coostack.cooparticlesapi.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int cooparticlesapi$getClickCount();

    @Accessor("clickCount")
    void cooparticlesapi$setClickCount(int clickCount);
}
