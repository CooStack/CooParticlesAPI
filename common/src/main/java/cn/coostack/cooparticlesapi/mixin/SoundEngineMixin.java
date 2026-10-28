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
    /** 原版活动声音到实际声道的映射，沿用原版线程所有权。 */
    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    /** 复用原版类别音量、声音音量与 Coo ducking 的统一计算入口。 */
    @Shadow
    private float calculateVolume(SoundInstance soundInstance) {
        throw new AssertionError();
    }

    /** 首次播放使用原版原始音量入口，额外应用与持续 tick 相同的 ducking。 */
    @ModifyExpressionValue(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
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

    /** 只修改按实例计算的重载，避免类别重载重复相乘。 */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void cooparticlesapi$applyDuckingToCalculatedVolume(
            SoundInstance soundInstance,
            CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(cir.getReturnValueF() * ClientSoundManager.duckVolumeMultiplier(soundInstance));
    }

    /**
     * 在声音 tick 末尾发布活动声道数，并在需要时刷新 ducking 后的声道音量。
     */
    @Inject(method = "tickInGameSound()V", at = @At("TAIL"))
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
