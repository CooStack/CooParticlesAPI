package cn.coostack.cooparticlesapi.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    @Final
    private static Map<InputConstants.Key, KeyMapping> MAP;

    @Inject(method = "click", at = @At("TAIL"))
    private static void cooparticlesapi$broadcastClick(InputConstants.Key key, CallbackInfo ci) {
        cooparticlesapi$forEachAdditionalMappingWithKey(key, KeyMappingMixin::cooparticlesapi$incrementClickCount);
    }

    @Inject(method = "set", at = @At("TAIL"))
    private static void cooparticlesapi$broadcastSet(InputConstants.Key key, boolean isDown, CallbackInfo ci) {
        cooparticlesapi$forEachAdditionalMappingWithKey(key, mapping -> mapping.setDown(isDown));
    }

    private static void cooparticlesapi$forEachAdditionalMappingWithKey(
        InputConstants.Key key,
        Consumer<KeyMapping> action
    ) {
        KeyMapping primary = MAP.get(key);
        for (KeyMapping mapping : ALL.values()) {
            if (mapping != primary && cooparticlesapi$usesKey(mapping, key)) {
                action.accept(mapping);
            }
        }
    }

    private static boolean cooparticlesapi$usesKey(KeyMapping mapping, InputConstants.Key key) {
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return mapping.matchesMouse(key.getValue());
        }
        if (key.getType() == InputConstants.Type.SCANCODE) {
            return mapping.matches(InputConstants.UNKNOWN.getValue(), key.getValue());
        }
        return mapping.matches(key.getValue(), 0);
    }

    private static void cooparticlesapi$incrementClickCount(KeyMapping mapping) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
        accessor.cooparticlesapi$setClickCount(accessor.cooparticlesapi$getClickCount() + 1);
    }
}
