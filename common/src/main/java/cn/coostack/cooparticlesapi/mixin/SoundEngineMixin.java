package cn.coostack.cooparticlesapi.mixin;

import cn.coostack.cooparticlesapi.supports.sound.ClientSoundManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 把 Coo ducking 应用到原版 SoundEngine，并发布当前实际声音声道数。
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow
    private float calculateVolume(SoundInstance soundInstance) {
        throw new AssertionError();
    }

    @ModifyExpressionValue(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"
            )
    )
    private float cooparticlesapi$applyDuckingToInitialVolume(
            float original,
            @Local(argsOnly = true) SoundInstance soundInstance
    ) {
        return original * ClientSoundManager.duckVolumeMultiplier(soundInstance);
    }

    @Inject(method = "calculateVolume", at = @At("RETURN"), cancellable = true)
    private void cooparticlesapi$applyDuckingToCalculatedVolume(
            SoundInstance soundInstance,
            CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(cir.getReturnValueF() * ClientSoundManager.duckVolumeMultiplier(soundInstance));
    }

    /**
     * 在声音 tick 末尾发布活动声道数，并在需要时刷新 ducking 后的声道音量。
     */
    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void cooparticlesapi$refreshDuckedVolumes(CallbackInfo ci) {
        ClientSoundManager.updateVanillaSoundInstanceCount(this.instanceToChannel.size());
        if (!ClientSoundManager.shouldRefreshSoundVolumes()) {
            return;
        }
        this.instanceToChannel.forEach((soundInstance, channelHandle) -> {
            float volume = this.calculateVolume(soundInstance);
            channelHandle.execute(channel -> channel.setVolume(volume));
        });
        ClientSoundManager.markSoundVolumesRefreshed();
    }
}
